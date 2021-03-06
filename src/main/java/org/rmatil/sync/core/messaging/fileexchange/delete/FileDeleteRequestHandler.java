package org.rmatil.sync.core.messaging.fileexchange.delete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The request handler for a FileDeleteExchange.
 *
 * @see FileDeleteExchangeHandler
 */
public class FileDeleteRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileDeleteExchangeHandler.class);

    /**
     * The storage adapter to access the synced folder
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The object store
     */
    protected IObjectStore objectStore;

    /**
     * The client to send responses
     */
    protected INode node;

    /**
     * The file delete request which have been received
     */
    protected FileDeleteRequest request;

    /**
     * The global event bus to send events to
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
    public void setNode(INode node) {
        this.node = node;
    }

    @Override
    public void setAccessManager(IAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public void setRequest(IRequest request) {
        if (! (request instanceof FileDeleteRequest)) {
            throw new IllegalArgumentException("Got request " + request.getClass().getName() + " but expected " + FileDeleteExchangeHandler.class.getName());
        }

        this.request = (FileDeleteRequest) request;
    }

    @Override
    public void run() {
        try {
            logger.info("Deleting path on " + this.request.getPathToDelete());

            TreePathElement pathToDelete;
            if ((null != this.request.getOwner() && this.node.getUser().getUserName().equals(this.request.getOwner())) ||
                    null != this.request.getFileId()) {
                // we have to use our path: if we are either the owner or a sharer
                pathToDelete = new TreePathElement(this.node.getIdentifierManager().getKey(this.request.getFileId()));
            } else {
                pathToDelete = new TreePathElement(this.request.getPathToDelete());
            }

            if (! this.node.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.WRITE, pathToDelete.getPath())) {
                // client has no access to delete the file
                logger.warn("Deletion failed due to missing access rights on file " + pathToDelete.getPath() + " for user " + this.request.getClientDevice().getUserName() + " on exchange " + this.request.getExchangeId());
                this.sendResponse(StatusCode.ACCESS_DENIED);
                return;
            }

            try {
                if (this.storageAdapter.exists(StorageType.DIRECTORY, pathToDelete) || this.storageAdapter.exists(StorageType.FILE, pathToDelete)) {
                    // create ignore events for all dir contents
                    List<TreePathElement> elementsToIgnore = new ArrayList<>();
                    elementsToIgnore.add(pathToDelete);

                    if (this.storageAdapter.isDir(pathToDelete)) {
                        // add all directory contents to the ignored files too
                        elementsToIgnore.addAll(this.storageAdapter.getDirectoryContents(pathToDelete));
                    }

                    for (TreePathElement element : elementsToIgnore) {
                        this.globalEventBus.publish(new IgnoreBusEvent(
                                new DeleteEvent(
                                        Paths.get(element.getPath()),
                                        Paths.get(element.getPath()).getFileName().toString(),
                                        "weIgnoreTheHash",
                                        System.currentTimeMillis()
                                )
                        ));

                        try {
                            logger.trace("Removing sharing information from object store for file " + element.getPath() + " and exchange " + this.request.getExchangeId());
                            // remove all connections to any sharers
                            PathObject deletedObject = this.objectStore.getObjectManager().getObjectForPath(element.getPath());
                            deletedObject.setSharers(new HashSet<>());
                            deletedObject.setIsShared(false);
                            deletedObject.setAccessType(null);
                            deletedObject.setOwner(null);

                            this.objectStore.getObjectManager().writeObject(deletedObject);
                        } catch (InputOutputException e) {
                            logger.error("Failed to remove sharing information from object store: " + e.getMessage());
                        }
                    }

                    this.storageAdapter.delete(pathToDelete);
                }
            } catch (InputOutputException e) {
                logger.error("Could not delete path " + pathToDelete.getPath() + ". Message: " + e.getMessage());
            }

            this.sendResponse(StatusCode.ACCEPTED);

        } catch (Exception e) {
            logger.error("Error in FileDeleteRequestHandler thread for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);

            try {
                this.sendResponse(StatusCode.ERROR);
            } catch (Exception e1) {
                logger.error("Failed to notify originating node about error in exchange " + this.request.getExchangeId() + ". Message: " + e1.getMessage(), e1);
            }
        }
    }

    /**
     * Sends a response with the given status code back to the sender
     *
     * @param statusCode The status code to use in the response
     */
    protected void sendResponse(StatusCode statusCode) {
        NodeLocation receiver = new NodeLocation(
                this.request.getClientDevice().getUserName(),
                this.request.getClientDevice().getClientDeviceId(),
                this.request.getClientDevice().getPeerAddress()
        );

        this.node.sendDirect(
                receiver,
                new FileDeleteResponse(
                        this.request.getExchangeId(),
                        statusCode,
                        new ClientDevice(
                                this.node.getUser().getUserName(),
                                this.node.getClientDeviceId(),
                                this.node.getPeerAddress()
                        ),
                        receiver
                )
        );
    }
}
