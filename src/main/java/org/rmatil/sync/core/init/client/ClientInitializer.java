package org.rmatil.sync.core.init.client;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.config.Config;
import org.rmatil.sync.network.core.Client;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.User;
import org.rmatil.sync.persistence.core.dht.DhtStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ClientInitializer implements IInitializer<IClient> {

    protected KeyPairGenerator keyPairGenerator;
    protected String           username;
    protected String           password;
    protected String           salt;
    protected int              port;

    protected IUser   user;
    protected IClient client;

    protected DhtStorageAdapter dhtStorageAdapter;
    protected ClientManager     clientManager;
    protected Config            networkConfig;

    public ClientInitializer(String username, String password, String salt, int port) {
        this.username = username;
        this.password = password;
        this.salt = salt;
        this.port = port;
    }

    @Override
    public IClient init()
            throws InitializationException {
        try {
            this.keyPairGenerator = KeyPairGenerator.getInstance("DSA");
        } catch (NoSuchAlgorithmException e) {
            throw new InitializationException(e);
        }

        KeyPair keyPair = this.keyPairGenerator.generateKeyPair();
        List<ClientLocation> clientLocations = new ArrayList<>();
        this.user = new User(
                this.username,
                this.password,
                this.salt,
                keyPair.getPublic(),
                keyPair.getPrivate(),
                clientLocations
        );

        networkConfig = Config.IPv4;
        networkConfig.setPort(this.port);

        this.client = new Client(networkConfig, this.user);

        return this.client;
    }

    @Override
    public void start()
            throws InitializationStartException {

        boolean isSuccess = this.client.start();

        if (! isSuccess) {
            throw new InitializationStartException("Could not start client");
        }

        // we can init the dht storage adapter only after
        // the peerDHT is started (i.e. built), otherwise we will
        // get a NullPointerException on the private/public key for protection
        this.dhtStorageAdapter = new DhtStorageAdapter(this.client.getPeerDht());
        this.clientManager = new ClientManager(
                this.dhtStorageAdapter,
                networkConfig.getLocationsContentKey(),
                networkConfig.getPrivateKeyContentKey(),
                networkConfig.getPublicKeyContentKey(),
                networkConfig.getDomainKey()
        );

        ClientLocation clientLocation = new ClientLocation(this.client.getPeerAddress());

        try {
            this.clientManager.addPrivateKey(this.user);
            this.clientManager.addPublicKey(this.user);
            this.clientManager.addClientLocation(this.user, clientLocation);
        } catch (InputOutputException e) {
            throw new InitializationStartException(e);
        }
    }

    @Override
    public void stop()
            throws InitializationStopException {

        try {
            this.clientManager.removeClientLocation(this.user, new ClientLocation(this.client.getPeerAddress()));
            // TODO: remove client public and private key
        } catch (InputOutputException e) {
            throw new InitializationStopException(e);
        }

        this.client.shutdown();
    }
}