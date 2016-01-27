package org.rmatil.sync.core.syncer.background.fetchobjectstore;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

/**
 * A fetch object store which causes the receiving client
 * to respond with the zipped object store
 */
public class FetchObjectStoreRequest extends ARequest {

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this requeust
     */
    public FetchObjectStoreRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses) {
        super(exchangeId, clientDevice, receiverAddresses);
    }
}