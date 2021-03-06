package org.rmatil.sync.core.messaging.fileexchange.demand;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.List;
import java.util.UUID;

/**
 * A request demanding particular parts of a file
 */
public class FileDemandRequest extends ARequest {

    private static final long serialVersionUID = - 9136592397626688426L;

    /**
     * The relative path to the file which should be returned
     */
    protected String relativeFilePath;

    /**
     * The counter which indicates which chunk should be requested
     */
    protected long chunkCounter;

    /**
     * @param exchangeId       The identifier of the file exchange
     * @param statusCode       The status code of the request
     * @param clientDevice     The client device which is requesting the file demand (i.e. this client)
     * @param relativeFilePath The relative path to the file which should be returned
     * @param chunkCounter     The chunk number which should returned in the corresponding response to this request
     */
    public FileDemandRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, String relativeFilePath, List<NodeLocation> receiverAddresses, long chunkCounter) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.relativeFilePath = relativeFilePath;
        this.chunkCounter = chunkCounter;
    }

    /**
     * Returns the relative path to the file which should be returned
     *
     * @return The relative path
     */
    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    /**
     * Returns the chunk index of the chunk which should be returned
     *
     * @return The chunk number to return
     */
    public long getChunkCounter() {
        return chunkCounter;
    }
}
