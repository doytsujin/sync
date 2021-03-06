package org.rmatil.sync.core.messaging.fileexchange.push;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.ShareNaming;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.eventbus.*;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.Sharer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FilePushRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FilePushRequestHandler.class);

    /**
     * The storage adapter to access the synchronized folder
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The object store to access versions
     */
    protected IObjectStore objectStore;

    /**
     * The client to send back messages
     */
    protected INode node;

    /**
     * The file push request from the sender
     */
    protected FilePushRequest request;

    /**
     * The global event bus to add ignore events
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The access manager to check for sharer's access to files
     */
    protected IAccessManager accessManager;

    @Override
    public void setStorageAdapter(ITreeStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public void setGlobalEventBus(MBassador<IBusEvent> globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void setNode(INode INode) {
        this.node = INode;
    }

    @Override
    public void setAccessManager(IAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof FilePushRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FilePushRequest.class.getName());
        }

        this.request = (FilePushRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            boolean fileIsChildFromSharedFolder = false;
            TreePathElement localPathElement;
            if ((null != this.request.getOwner() && this.node.getUser().getUserName().equals(this.request.getOwner())) ||
                    null != this.request.getFileId()) {
                // we have to use our path: if we are either the owner or a sharer
                String pathToFile = this.node.getIdentifierManager().getKey(this.request.getFileId());

                if (null == pathToFile) {
                    // this is a file which was created in a shared folder
                    // and does not exist yet on our side.
                    // -> resolve access permissions and put it in correct folder

                    fileIsChildFromSharedFolder = true;

                    // get access permissions first
                    AccessType accessType = null;
                    for (Sharer sharer : this.request.getSharers()) {
                        if (this.node.getUser().getUserName().equals(sharer.getUsername())) {
                            accessType = sharer.getAccessType();
                            break;
                        }
                    }

                    if (null == accessType) {
                        logger.error("Failed to get AccessType since no sharer has the same username as we do. Aborting FilePushExchange " + this.request.getExchangeId());
                        this.sendResponse(this.createResponse(- 1));
                        return;
                    }

                    try {
                        String relPathInSharedFolder = ShareNaming.getRelativePathToSharedFolderByOwner(this.storageAdapter, this.objectStore, this.request.getRelativeFilePath(), this.request.getOwner());
                        Path relPathToSharedFolder = Paths.get(relPathInSharedFolder);

                        if (relPathToSharedFolder.getNameCount() > 1) {
                            Path parent;
                            if (AccessType.WRITE == accessType) {
                                parent = Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(relPathToSharedFolder.subpath(0, relPathToSharedFolder.getNameCount() - 1));
                            } else {
                                parent = Paths.get(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName()).resolve(relPathToSharedFolder.subpath(0, relPathToSharedFolder.getNameCount() - 1));
                            }

                            // place the file in the root if it's parent does not exist anymore
                            if (! this.storageAdapter.exists(StorageType.DIRECTORY, new TreePathElement(parent.toString()))) {
                                logger.info("Parent of file " + this.request.getRelativeFilePath() + " does not exist (anymore). Placing file at root of shared dir");
                                relPathToSharedFolder = relPathToSharedFolder.getFileName();
                            }
                        }

                        // find an unique file path and store it in the DHT
                        String relativePath;
                        if (AccessType.WRITE == accessType) {
                            relativePath = Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + relPathToSharedFolder.toString();
                        } else {
                            relativePath = Config.DEFAULT.getSharedWithOthersReadOnlyFolderName() + "/" + relPathToSharedFolder.toString();
                        }

                        relativePath = ShareNaming.getUniqueFileName(this.storageAdapter, relativePath, this.request.isFile());
                        // add relativePath <-> fileId to DHT
                        this.node.getIdentifierManager().addIdentifier(relativePath, this.request.getFileId());
                        localPathElement = new TreePathElement(relativePath);

                    } catch (InputOutputException e) {
                        logger.error("Failed to get relative path to shared folder (by owner). Message: " + e.getMessage() + ". Aborting filePushExchange " + this.request.getExchangeId());
                        this.sendResponse(this.createResponse(- 1));
                        return;
                    }

                } else {
                    localPathElement = new TreePathElement(pathToFile);
                }
            } else {
                localPathElement = new TreePathElement(this.request.getRelativeFilePath());

                // only check access if the file is not from a sharer
                // to prevent errors when the pathObject is not yet created
                if (! this.node.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.WRITE, localPathElement.getPath())) {
                    logger.warn("Failed to write chunk " + this.request.getChunkCounter() + " for file " + localPathElement.getPath() + " due to missing access rights of user " + this.request.getClientDevice().getUserName() + " on exchange " + this.request.getExchangeId());
                    this.sendResponse(this.createResponse(- 1));
                    return;
                }
            }

            logger.info("Writing chunk " + this.request.getChunkCounter() + " for file " + localPathElement.getPath() + " for exchangeId " + this.request.getExchangeId());

            StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

            if (! fileIsChildFromSharedFolder && 0 == this.request.getChunkCounter()) {
                // add sharers to object store only on the first request
                this.publishAddOwnerAndAccessTypeToObjectStore(localPathElement);
                this.publishAddSharerToObjectStore(localPathElement);
            } else if (fileIsChildFromSharedFolder) {
                this.publishAddOwnerAndAccessTypeForChildOfSharedFolder(localPathElement);
            }

            if (this.request.isFile() && StatusCode.FILE_CHANGED.equals(this.request.getStatusCode()) &&
                    this.storageAdapter.exists(storageType, localPathElement)) {
                // we have to clean up the file again to prevent the
                // file being larger than expected after the change
                this.publishIgnoreModifyEvent(localPathElement);
                this.storageAdapter.persist(storageType, localPathElement, new byte[0]);
            }

            if (this.request.isFile()) {
                try {
                    if (! this.storageAdapter.exists(StorageType.FILE, localPathElement)) {
                        this.publishIgnoreCreateEvent(localPathElement);
                    } else {
                        this.publishIgnoreModifyEvent(localPathElement);
                    }

                    // some file systems modify the file again
                    this.publishIgnoreModifyEvent(localPathElement);

                    this.storageAdapter.persist(StorageType.FILE, localPathElement, this.request.getChunkCounter() * this.request.getChunkSize(), this.request.getData().getContent());
                } catch (InputOutputException e) {
                    logger.error("Could not write chunk " + this.request.getChunkCounter() + " of file " + localPathElement.getPath() + ". Message: " + e.getMessage(), e);
                }
            } else {
                try {
                    if (! this.storageAdapter.exists(StorageType.DIRECTORY, localPathElement)) {
                        this.publishIgnoreCreateEvent(localPathElement);
                        this.storageAdapter.persist(StorageType.DIRECTORY, localPathElement, null);
                    }
                } catch (InputOutputException e) {
                    logger.error("Could not create directory " + localPathElement.getPath() + ". Message: " + e.getMessage());
                }
            }

            long requestingChunk = this.request.getChunkCounter();
            // chunk counter starts at 0
            if (this.request.getChunkCounter() + 1 == this.request.getTotalNrOfChunks()) {
                // now check that we got the same checksum for the file
                try {
                    String checksum = "";

                    // dirs may not have a checksum
                    if (this.request.isFile()) {
                        checksum = this.storageAdapter.getChecksum(localPathElement);
                    }

                    if (null == this.request.getChecksum() || this.request.getChecksum().equals(checksum)) {
                        logger.info("Checksums match. Stopping exchange " + this.request.getExchangeId());
                        // checksums match or the other side failed to compute one
                        // -> indicate we got all chunks
                        requestingChunk = - 1;
                        // clean up all modify events
                        this.globalEventBus.publish(new CleanModifyIgnoreEventsBusEvent(
                                localPathElement.getPath()
                        ));
                    } else {
                        logger.info("Checksums do not match (local: " + checksum + "/request:" + this.request.getChecksum() + "). Restarting to push file for exchange " + this.request.getExchangeId());
                        // restart to fetch the whole file
                        requestingChunk = 0;

                        this.publishIgnoreModifyEvent(localPathElement);
                        this.storageAdapter.persist(storageType, localPathElement, new byte[0]);
                    }
                } catch (InputOutputException e) {
                    logger.error("Failed to generate the checksum for file " + localPathElement.getPath() + " on exchange " + this.request.getExchangeId() + ". Accepting the file. Message: " + e.getMessage());
                    requestingChunk = - 1;
                }
            } else {
                requestingChunk++;
            }

            this.sendResponse(this.createResponse(requestingChunk));
        } catch (Exception e) {
            logger.error("Error in FilePushRequestHandler for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);

            try {
                this.sendResponse(this.createResponse(StatusCode.ERROR, - 1));
            } catch (Exception e1) {
                logger.error("Failed to notify originating node about error in exchange " + this.request.getExchangeId() + ". Message: " + e1.getMessage(), e1);
            }
        }
    }

    /**
     * Creates a file push response with the given chunk counter
     *
     * @param requestingChunk The chunk to request from the other client
     *
     * @return The created FilePushResponse
     */
    protected FilePushResponse createResponse(long requestingChunk) {
        return this.createResponse(StatusCode.ACCEPTED, requestingChunk);
    }

    /**
     * Create an error response with the given status code
     * and -1 as requesting chunk.
     *
     * @param statusCode The status code of the error response
     *
     * @return The error response
     */
    protected FilePushResponse createResponse(StatusCode statusCode, long requestingChunk) {
        return new FilePushResponse(
                this.request.getExchangeId(),
                statusCode,
                new ClientDevice(
                        this.node.getUser().getUserName(),
                        this.node.getClientDeviceId(),
                        this.node.getPeerAddress()
                ),
                this.request.getRelativeFilePath(),
                new NodeLocation(
                        this.request.getClientDevice().getUserName(),
                        this.request.getClientDevice().getClientDeviceId(),
                        this.request.getClientDevice().getPeerAddress()
                ),
                requestingChunk
        );
    }

    /**
     * Sends the given response back to the client
     *
     * @param iResponse The response to send back
     */
    protected void sendResponse(IResponse iResponse) {
        if (null == this.node) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.node.sendDirect(iResponse.getReceiverAddress(), iResponse);
    }

    protected void publishIgnoreModifyEvent(IPathElement pathElement) {
        this.globalEventBus.publish(new IgnoreBusEvent(
                new ModifyEvent(
                        Paths.get(pathElement.getPath()),
                        Paths.get(pathElement.getPath()).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));

    }

    protected void publishIgnoreCreateEvent(IPathElement pathElement) {
        this.globalEventBus.publish(new IgnoreBusEvent(
                new CreateEvent(
                        Paths.get(pathElement.getPath()),
                        Paths.get(pathElement.getPath()).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));
    }

    protected void publishAddSharerToObjectStore(IPathElement pathElement) {
        this.globalEventBus.publish(new AddSharerToObjectStoreBusEvent(
                pathElement.getPath(),
                this.request.getSharers()
        ));
    }

    protected void publishAddOwnerAndAccessTypeToObjectStore(IPathElement pathElement) {
        this.globalEventBus.publish(new AddOwnerAndAccessTypeToObjectStoreBusEvent(
                this.request.getOwner(),
                this.request.getAccessType(),
                pathElement.getPath()
        ));
    }

    protected void publishAddOwnerAndAccessTypeForChildOfSharedFolder(IPathElement pathElement) {
        AccessType accessType = null;
        for (Sharer sharer : this.request.getSharers()) {
            if (this.node.getUser().getUserName().equals(sharer.getUsername())) {
                accessType = sharer.getAccessType();
                break;
            }
        }

        this.globalEventBus.publish(new AddOwnerAndAccessTypeToObjectStoreBusEvent(
                this.request.getClientDevice().getUserName(),
                accessType,
                pathElement.getPath()
        ));
    }
}
