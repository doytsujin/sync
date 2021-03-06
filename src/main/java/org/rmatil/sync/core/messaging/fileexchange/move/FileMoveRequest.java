package org.rmatil.sync.core.messaging.fileexchange.move;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Send this request to all clients when
 * moving a directory or a file happened.
 */
public class FileMoveRequest extends ARequest {

    private static final long serialVersionUID = - 5770204811691749128L;

    /**
     * The old relative path
     */
    protected String oldPath;

    /**
     * The new relative path
     */
    protected String newPath;

    /**
     * Whether the file represents a directory
     */
    protected boolean isFile;

    /**
     * @param exchangeId      The exchange id
     * @param statusCode      The status code of this request
     * @param clientDevice    The client device sending this request
     * @param receiverAddress The receiver address
     * @param oldPath         The old path
     * @param newPath         The new path
     * @param isFile          If the path represents a file
     */
    public FileMoveRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, NodeLocation receiverAddress, String oldPath, String newPath, boolean isFile) {
        super(exchangeId, statusCode, clientDevice, new ArrayList<>());
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.isFile = isFile;

        super.receiverAddresses.add(receiverAddress);
    }

    /**
     * Returns the old path of the file resp. directory
     *
     * @return The old path
     */
    public String getOldPath() {
        return oldPath;
    }

    /**
     * Returns the new path of the file resp. directory
     *
     * @return The new path
     */
    public String getNewPath() {
        return newPath;
    }

    /**
     * Returns whether the path represents a directory or a file
     *
     * @return True, if it is a file, false otherwise
     */
    public boolean isFile() {
        return isFile;
    }
}
