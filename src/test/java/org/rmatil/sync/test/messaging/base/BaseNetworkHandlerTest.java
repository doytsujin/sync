package org.rmatil.sync.test.messaging.base;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.config.IBusConfiguration;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.init.client.ClientInitializer;
import org.rmatil.sync.core.init.client.LocalStateObjectDataReplyHandler;
import org.rmatil.sync.core.init.eventaggregator.EventAggregatorInitializer;
import org.rmatil.sync.core.init.objecstore.ObjectStoreFileChangeListener;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteRequest;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequest;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequest;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareRequest;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedRequest;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareRequest;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedRequest;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedRequestHandler;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.core.security.AccessManager;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequest;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequestHandler;
import org.rmatil.sync.core.syncer.file.FileSyncer;
import org.rmatil.sync.core.syncer.file.SyncFileChangeListener;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ConnectionConfiguration;
import org.rmatil.sync.network.core.Node;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.network.core.model.User;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.base.BaseTest;
import org.rmatil.sync.test.config.Config;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseNetworkHandlerTest extends BaseTest {

    protected static final int    PORT_CLIENT_1 = Config.DEFAULT.getPort1();
    protected static final int    PORT_CLIENT_2 = Config.DEFAULT.getPort2();
    protected static final String USERNAME      = Config.DEFAULT.getUsername();
    protected static final String PASSWORD      = Config.DEFAULT.getPassword();
    protected static final String SALT          = Config.DEFAULT.getSalt();

    protected static final String USERNAME_2 = Config.DEFAULT.getUsername2();
    protected static final String PASSWORD_2 = Config.DEFAULT.getPassword2();
    protected static final String SALT_2     = Config.DEFAULT.getSalt2();

    protected static KeyPair KEYPAIR_1;
    protected static KeyPair KEYPAIR_2;
    protected static IUser   USER_1;
    protected static IUser   USER_2;

    protected static final UUID CLIENT_ID_1 = UUID.randomUUID();
    protected static final UUID CLIENT_ID_2 = UUID.randomUUID();

    protected static MBassador<IBusEvent> GLOBAL_EVENT_BUS_1;
    protected static MBassador<IBusEvent> GLOBAL_EVENT_BUS_2;

    protected static GlobalEventBusDummyListener EVENT_BUS_LISTENER_1;
    protected static GlobalEventBusDummyListener EVENT_BUS_LISTENER_2;

    protected static ITreeStorageAdapter STORAGE_ADAPTER_1;
    protected static IObjectStore        OBJECT_STORE_1;

    protected static ITreeStorageAdapter STORAGE_ADAPTER_2;
    protected static IObjectStore        OBJECT_STORE_2;

    protected static INode CLIENT_1;
    protected static INode CLIENT_2;

    protected static FileSyncer FILE_SYNCER_1;
    protected static FileSyncer FILE_SYNCER_2;

    protected static IEventAggregator EVENT_AGGREGATOR_1;
    protected static IEventAggregator EVENT_AGGREGATOR_2;

    protected static ClientDevice CLIENT_DEVICE_1;
    protected static ClientDevice CLIENT_DEVICE_2;

    protected static INodeManager CLIENT_MANAGER_1;
    protected static INodeManager CLIENT_MANAGER_2;

    protected static List<NodeLocation> CLIENT_LOCATIONS_1;

    @BeforeClass
    public static void setUp()
            throws IOException, InputOutputException {

        createTestDirs();
        createObjectStoreDirs();

        KEYPAIR_1 = createKeyPair();
        KEYPAIR_2 = createKeyPair();

        USER_1 = createUser();
        USER_2 = createUser2();

        GLOBAL_EVENT_BUS_1 = createGlobalEventBus();
        GLOBAL_EVENT_BUS_2 = createGlobalEventBus();

        EVENT_BUS_LISTENER_1 = new GlobalEventBusDummyListener();
        EVENT_BUS_LISTENER_2 = new GlobalEventBusDummyListener();

        GLOBAL_EVENT_BUS_1.subscribe(EVENT_BUS_LISTENER_1);
        GLOBAL_EVENT_BUS_2.subscribe(EVENT_BUS_LISTENER_2);

        STORAGE_ADAPTER_1 = new LocalStorageAdapter(ROOT_TEST_DIR1);
        STORAGE_ADAPTER_2 = new LocalStorageAdapter(ROOT_TEST_DIR2);

        OBJECT_STORE_1 = createObjectStore(STORAGE_ADAPTER_1);
        OBJECT_STORE_2 = createObjectStore(STORAGE_ADAPTER_2);

        CLIENT_1 = createClient(
                new ConnectionConfiguration(
                        CLIENT_ID_1.toString(),
                        PORT_CLIENT_1,
                        0L,
                        20000L,
                        20000L,
                        5000L,
                        false
                ),
                USER_1,
                STORAGE_ADAPTER_1,
                OBJECT_STORE_1,
                GLOBAL_EVENT_BUS_1,
                null
        );

        CLIENT_2 = createClient(
                new ConnectionConfiguration(
                        CLIENT_ID_2.toString(),
                        PORT_CLIENT_2,
                        0L,
                        20000L,
                        20000L,
                        5000L,
                        false
                ),
                USER_1,
                STORAGE_ADAPTER_2,
                OBJECT_STORE_2,
                GLOBAL_EVENT_BUS_2,
                new RemoteClientLocation(
                        CLIENT_1.getPeerAddress().inetAddress().getHostName(),
                        CLIENT_1.getPeerAddress().tcpPort()
                )
        );


        CLIENT_MANAGER_1 = CLIENT_1.getNodeManager();
        CLIENT_MANAGER_2 = CLIENT_2.getNodeManager();

        FILE_SYNCER_1 = createFileSyncer(CLIENT_1, ROOT_TEST_DIR1, OBJECT_STORE_1, GLOBAL_EVENT_BUS_1);
        FILE_SYNCER_2 = createFileSyncer(CLIENT_2, ROOT_TEST_DIR2, OBJECT_STORE_2, GLOBAL_EVENT_BUS_2);

        // Note: start the event aggregator manually in the subclasses
        EVENT_AGGREGATOR_1 = createEventAggregator(STORAGE_ADAPTER_1, OBJECT_STORE_1, FILE_SYNCER_1, GLOBAL_EVENT_BUS_1);
        EVENT_AGGREGATOR_2 = createEventAggregator(STORAGE_ADAPTER_2, OBJECT_STORE_2, FILE_SYNCER_2, GLOBAL_EVENT_BUS_2);

        ObjectStoreFileChangeListener listener1 = new ObjectStoreFileChangeListener(OBJECT_STORE_1);
        GLOBAL_EVENT_BUS_1.subscribe(listener1);
        EVENT_AGGREGATOR_1.addListener(listener1);

        ObjectStoreFileChangeListener listener2 = new ObjectStoreFileChangeListener(OBJECT_STORE_2);
        GLOBAL_EVENT_BUS_2.subscribe(listener2);
        EVENT_AGGREGATOR_2.addListener(listener2);

        CLIENT_DEVICE_1 = new ClientDevice(USERNAME, CLIENT_ID_1, CLIENT_1.getPeerAddress());
        CLIENT_DEVICE_2 = new ClientDevice(USERNAME, CLIENT_ID_2, CLIENT_2.getPeerAddress());

        CLIENT_LOCATIONS_1 = CLIENT_MANAGER_2.getNodeLocations(USER_1.getUserName());
    }

    @AfterClass
    public static void tearDown() {
        EVENT_AGGREGATOR_1.stop();
        EVENT_AGGREGATOR_2.stop();

        CLIENT_2.shutdown();
        CLIENT_1.shutdown();

        deleteTestDirs();
    }

    /**
     * Generates the public private key pair
     *
     * @return The public private key pair
     */
    private static KeyPair createKeyPair() {
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new InitializationException(e);
        }

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Creates the global event bus to use
     *
     * @return The global event bus to use
     */
    protected static MBassador<IBusEvent> createGlobalEventBus() {
        // Use feature driven configuration to have more control over the configuration details
        return new MBassador<>(new BusConfiguration()
                .addFeature(Feature.SyncPubSub.Default())
                .addFeature(Feature.AsynchronousHandlerInvocation.Default())
                .addFeature(Feature.AsynchronousMessageDispatch.Default())
                .addPublicationErrorHandler(new IPublicationErrorHandler.ConsoleLogger())
                .setProperty(IBusConfiguration.Properties.BusId, "P2P-Sync-TEST-GlobalEventBus-" + UUID.randomUUID().toString())); // this is used for identification in #toString
    }

    /**
     * Creates a new user
     *
     * @return The created user
     */
    private static IUser createUser() {
        return new User(
                USERNAME,
                PASSWORD,
                SALT,
                KEYPAIR_1.getPublic(),
                KEYPAIR_1.getPrivate(),
                new ArrayList<>()
        );
    }

    /**
     * Creates a new user
     *
     * @return The created user
     */
    private static IUser createUser2() {
        return new User(
                USERNAME_2,
                PASSWORD_2,
                SALT_2,
                KEYPAIR_2.getPublic(),
                KEYPAIR_2.getPrivate(),
                new ArrayList<>()
        );
    }

    /**
     * Creates and starts a client with the provided configuration
     *
     * @param connectionConfiguration The connection configuration used to create the client
     * @param user                    The user to which the created client should belong
     * @param storageAdapter          The storage adapter to use for the local state object data reply handler
     * @param objectStore             The object store to access versions
     * @param globalEventBus          The global event bus for the client
     * @param bootstrapLocation       The bootstrap location to use. May be null to start as bootstrap peer
     *
     * @return The configured and started client
     */
    protected static INode createClient(ConnectionConfiguration connectionConfiguration, IUser user, ITreeStorageAdapter storageAdapter, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus, RemoteClientLocation bootstrapLocation) {
        INode client = new Node(null, user, null);
        LocalStateObjectDataReplyHandler objectDataReplyHandler = new LocalStateObjectDataReplyHandler(
                storageAdapter,
                objectStore,
                client,
                globalEventBus,
                null,
                null,
                new AccessManager(objectStore)
        );
        // specify protocol
        objectDataReplyHandler.addRequestCallbackHandler(FileOfferRequest.class, FileOfferRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FilePushRequest.class, FilePushRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileDeleteRequest.class, FileDeleteRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileMoveRequest.class, FileMoveRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FetchObjectStoreRequest.class, FetchObjectStoreRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileDemandRequest.class, FileDemandRequestHandler.class);

        // file sharing
        objectDataReplyHandler.addRequestCallbackHandler(ShareRequest.class, ShareRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(SharedRequest.class, SharedRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(UnshareRequest.class, UnshareRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(UnsharedRequest.class, UnsharedRequestHandler.class);


        ClientInitializer clientInitializer = new ClientInitializer(connectionConfiguration, objectDataReplyHandler, user, bootstrapLocation);
        client = clientInitializer.init();
        clientInitializer.start();

        objectDataReplyHandler.setNode(client);

        return client;
    }

    /**
     * Creates a new file syncer
     *
     * @param client         The client to use for sending messages
     * @param rootPath       The root path of the synchronized folder
     * @param objectStore    The object store to access versions
     * @param globalEventBus The global event bus to publish events to
     *
     * @return The created file syncer
     */
    protected static FileSyncer createFileSyncer(INode client, Path rootPath, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus) {
        FileSyncer fileSyncer = new FileSyncer(
                client.getUser(),
                client,
                client.getNodeManager(),
                new LocalStorageAdapter(rootPath),
                objectStore,
                globalEventBus
        );

        globalEventBus.subscribe(fileSyncer);

        return fileSyncer;
    }

    /**
     * Create and start event aggregator
     *
     * @param treeStorageAdapter A tree storage adapter pointing to the  root path to watch for changes
     * @param objectStore        The object store to check for versions
     * @param fileSyncer         The file syncer to create the listener for events
     * @param globalEventBus     The global event bus to add events
     *
     * @return The started event aggregator
     */
    protected static IEventAggregator createEventAggregator(ITreeStorageAdapter treeStorageAdapter, IObjectStore objectStore, FileSyncer fileSyncer, MBassador<IBusEvent> globalEventBus) {
        // Add sync file change listener to event aggregator
        SyncFileChangeListener syncFileChangeListener = new SyncFileChangeListener(fileSyncer);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(syncFileChangeListener);

        globalEventBus.subscribe(syncFileChangeListener);

        List<IEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(syncFileChangeListener);

        // Init event aggregator
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(
                Paths.get(treeStorageAdapter.getRootDir().getPath())
                        .relativize(Paths.get(treeStorageAdapter.getRootDir().getPath()).resolve(".sync"))
        );

        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(
                treeStorageAdapter,
                objectStore,
                eventListeners,
                ignoredPaths,
                new ArrayList<>(),
                25000L
        );

        return eventAggregatorInitializer.init();
    }
}
