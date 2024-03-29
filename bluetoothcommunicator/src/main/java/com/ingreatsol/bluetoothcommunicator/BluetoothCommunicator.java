/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ingreatsol.bluetoothcommunicator;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.ingreatsol.bluetoothcommunicator.tools.BluetoothTools;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * This class allows you to communicate in P2P mode between two or more android devices.
 * It also automatically implements (they are active by default) reconnection in case of temporary connection loss, reliable message sending,
 * splitting and rebuilding of long messages, sending raw data in addition to text messages and a message queue in order to always send the messages (and always in the right order)
 * even in case of connection problems (they will be sent as soon as the connection is restored)
 * <br /><br />
 * First create a bluetooth communicator object, it is the object that handles all operations of bluetooth low energy library, if you want to manage
 * the bluetooth connections in multiple activities I suggest you to save this object as an attribute of a custom class that extends Application and
 * create a getter so you can access to bluetoothCommunicator from any activity or service with:
 * <pre>{@code
 * ((custom class name) getApplication()).getBluetoothCommunicator();
 * }</pre>
 * Next step is to initialize bluetoothCommunicator, the parameters are: a context, the name by which the other devices will see us (limited to 18 characters
 * and can be only characters listed in BluetoothTools.getSupportedUTFCharacters(context) because the number of bytes for advertising beacon is limited) and the strategy
 * (for now the only supported strategy is BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION)
 * <pre>{@code
 * bluetoothCommunicator = new BluetoothCommunicator(this, "device name", BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION);
 * }</pre>
 * Then add the bluetooth communicator callback, the callback will listen for all events of bluetooth communicator:
 * <pre>{@code
 * bluetoothCommunicator.addCallback(new BluetoothCommunicator.Callback() {
 *     @Override
 *     public void onBluetoothLeNotSupported() {
 *         super.onBluetoothLeNotSupported();
 *
 *         Notify that bluetooth low energy is not compatible with this device
 *     }
 *
 *     @Override
 *     public void onAdvertiseStarted() {
 *         super.onAdvertiseStarted();
 *
 *         Notify that advertise has started, if you want to do something after the start of advertising do it here, because
 *         after startAdvertise there is no guarantee that advertise is really started (it is delayed)
 *     }
 *
 *     @Override
 *     public void onDiscoveryStarted() {
 *         super.onDiscoveryStarted();
 *
 *         Notify that discovery has started, if you want to do something after the start of discovery do it here, because
 *         after startDiscovery there is no guarantee that discovery is really started (it is delayed)
 *     }
 *
 *     @Override
 *     public void onAdvertiseStopped() {
 *         super.onAdvertiseStopped();
 *
 *         Notify that advertise has stopped, if you want to do something after the stop of advertising do it here, because
 *         after stopAdvertising there is no guarantee that advertise is really stopped (it is delayed)
 *     }
 *
 *     @Override
 *     public void onDiscoveryStopped() {
 *         super.onDiscoveryStopped();
 *
 *         Notify that discovery has stopped, if you want to do something after the stop of discovery do it here, because
 *         after stopDiscovery there is no guarantee that discovery is really stopped (it is delayed)
 *     }
 *
 *     @Override
 *     public void onPeerFound(Peer peer) {
 *         super.onPeerFound(peer);
 *
 *         Here for example you can save peer in a list or anywhere you want and when the user
 *         choose a peer you can call bluetoothCommunicator.connect(peer founded) but if you want to
 *         use a peer for connect you have to have peer updated (see onPeerUpdated or onPeerLost), if you use a
 *         non updated peer the connection might fail
 *         instead if you want to immediate connect where peer is found you can call bluetoothCommunicator.connect(peer) here
 *     }
 *
 *     @Override
 *     public void onPeerLost(Peer peer){
 *         super.onPeerLost(peer);
 *
 *         It means that a peer is out of range or has interrupted the advertise,
 *         here you can delete the peer lost from a eventual collection of founded peers
 *     }
 *
 *     @Override
 *     public void onPeerUpdated(Peer peer,Peer newPeer){
 *         super.onPeerUpdated(peer,newPeer);
 *
 *         It means that a founded peer (or connected peer) has changed (name or address or other things),
 *         if you have a collection of founded peers, you need to replace peer with newPeer if you want to connect successfully to that peer.
 *
 *         In case the peer updated is connected and you have saved connected peers you have to update the peer if you want to successfully
 *         send a message or a disconnection request to that peer.
 *     }
 *
 *     @Override
 *     public void onConnectionRequest(Peer peer){
 *         super.onConnectionRequest(peer);
 *
 *         It means you have received a connection request from another device (peer) (that have called connect)
 *         for accept the connection request and start connection call bluetoothCommunicator.acceptConnection(peer);
 *         for refusing call bluetoothCommunicator.rejectConnection(peer); (the peer must be the peer argument of onConnectionRequest)
 *     }
 *
 *     @Override
 *     public void onConnectionSuccess(Peer peer,int source){
 *         super.onConnectionSuccess(peer,source);
 *
 *         This means that you have accepted the connection request using acceptConnection or the other
 *         device has accepted your connection request and the connection is complete, from now on you
 *         can send messages or data (or disconnection request) to this peer until onDisconnected
 *
 *         To send messages to all connected peers you need to create a message with a context, a header, represented by a
 *         single character string (you can use a header to distinguish between different types of messages, or you can ignore
 *         it and use a random character), the text of the message, or a series of bytes if you want to send any kind of data
 *         and the peer you want to send the message to (must be connected to avoid errors), example:
 *         new Message(context,"a","hello world",peer); If you want to send message to a specific peer you have to set
 *         the sender of the message with the corresponding peer.
 *
 *         To send disconnection request to connected peer you need to call bluetoothCommunicator.disconnect(peer);
 *     }
 *
 *     @Override
 *     public void onConnectionFailed(Peer peer,int errorCode){
 *         super.onConnectionFailed(peer,errorCode);
 *
 *         This means that your connection request is rejected or has other problems,
 *         to know the cause of the failure see errorCode (BluetoothCommunicator.CONNECTION_REJECTED
 *         means rejected connection and BluetoothCommunicator.ERROR means generic error)
 *     }
 *
 *     @Override
 *     public void onConnectionLost(Peer peer){
 *         super.onConnectionLost(peer);
 *
 *         This means that a connected peer has lost the connection with you and the library is trying
 *         to restore it, in this case you can update the gui to notify this problem.
 *
 *         You can still send messages in this situation, all sent messages are put in a queue
 *         and sent as soon as the connection is restored
 *     }
 *
 *     @Override
 *     public void onConnectionResumed(Peer peer){
 *         super.onConnectionResumed(peer);
 *
 *         Means that connection lost is resumed successfully
 *     }
 *
 *     @Override
 *     public void onMessageReceived(Message message,int source){
 *         super.onMessageReceived(message,source);
 *
 *         Means that you have received a message containing TEXT, for know the sender you can call message.getSender() that return
 *         the peer that have sent the message, you can ignore source, it indicate only if you have received the message
 *         as client or as server
 *     }
 *
 *     @Override
 *     public void onDataReceived(Message data,int source){
 *         super.onDataReceived(data,source);
 *
 *         Means that you have received a message containing DATA, for know the sender you can call message.getSender() that return
 *         the peer that have sent the message, you can ignore source, it indicate only if you have received the message
 *         as client or as server
 *     }
 *
 *     @Override
 *     public void onDisconnected(Peer peer,int peersLeft){
 *         super.onDisconnected(peer,peersLeft);
 *
 *         Means that the peer is disconnected, peersLeft indicate the number of connected peers remained
 *     }
 *
 *     @Override
 *     public void onDisconnectionFailed(){
 *         super.onDisconnectionFailed();
 *
 *         Means that a disconnection is failed, super.onDisconnectionFailed will reactivate bluetooth for forcing disconnection
 *         (however the disconnection will be notified in onDisconnection)
 *     }
 * });
 * }</pre>
 * Finally you can start discovery and/or advertising:
 * <pre>{@code
 * bluetoothCommunicator.startAdvertising();
 * bluetoothCommunicator.startDiscovery();
 * }</pre>
 * All other actions that can be done are explained with the comments in the code of callback I wrote before.
 * <br /><br />
 * To use this library add these permissions to your manifest:
 * <pre>{@code
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
 * <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 * }</pre>
 */
public class BluetoothCommunicator {
    // constants
    public static final int CLIENT = 0;
    public static final int SERVER = 1;
    public static final int SUCCESS = 0;
    public static final int CONNECTION_REJECTED = 1;
    public static final int ERROR = -1;
    public static final int ALREADY_STARTED = -3;
    public static final int ALREADY_STOPPED = -4;
    public static final int NOT_MAIN_THREAD = -5;
    public static final int DESTROYING = -6;
    public static final int BLUETOOTH_LE_NOT_SUPPORTED = -7;
    public static final int STRATEGY_P2P_WITH_RECONNECTION = 2;
    // variables
    private boolean advertising = false;
    private boolean discovering = false;
    private boolean destroying = false;
    private final int strategy;
    private final String uniqueName;
    private ArrayDeque<Message> pendingMessages = new ArrayDeque<>();
    private ArrayDeque<Message> pendingData = new ArrayDeque<>();
    // objects
    @NonNull
    private final Context context;
    @Nullable
    private final BluetoothAdapter bluetoothAdapter;
    @Nullable
    private BluetoothConnectionServer connectionServer;
    @Nullable
    private BluetoothConnectionClient connectionClient;
    private final ArrayList<Callback> clientCallbacks = new ArrayList<>();
    private final Handler mainHandler;
    private final AdvertiseCallback advertiseCallback;
    private final ScanCallback discoveryCallback;
    private final Object messagesLock = new Object();
    private final Object dataLock = new Object();
    private final Object bluetoothLock = new Object();
    ParcelUuid uuidService = new ParcelUuid(BluetoothConnection.APP_UUID);

    /**
     * Constructor of BluetoothCommunicator
     *
     * @param context context
     */
    public BluetoothCommunicator(@NonNull final Context context) {
        this.context = context;
        this.strategy = STRATEGY_P2P_WITH_RECONNECTION;
        this.uniqueName = BluetoothTools.generateBluetoothNameId(context);
        mainHandler = new Handler(Looper.getMainLooper());

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
            }

        };
        discoveryCallback = new ScanCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public synchronized void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                switch (callbackType) {
                    case ScanSettings.CALLBACK_TYPE_FIRST_MATCH: {
                        BluetoothDevice device1 = result.getDevice();
                        if (result.getScanRecord() != null && connectionClient != null) {
                            String name = result.getScanRecord().getDeviceName();
                            String uniqueName = new String(result.getScanRecord().getServiceData(uuidService), StandardCharsets.UTF_8);
                            if (name != null && name.length() > 0) {
                                Peer peerFound = new Peer(device1, name, uniqueName, false);
                                if (connectionClient.getReconnectingPeers().contains(peerFound.toString())) {
                                    connectionClient.onReconnectingPeerFound(peerFound);
                                } else {
                                    notifyPeerFound(peerFound);
                                }
                            }
                        }
                        break;
                    }
                    case ScanSettings.CALLBACK_TYPE_MATCH_LOST: {
                        BluetoothDevice device1 = result.getDevice();
                        notifyPeerLost(new Peer(device1, false));
                        break;
                    }
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                initializeConnection();
            }
        }

        if (isBluetoothLeSupported() != SUCCESS) {
            notifyBluetoothLeNotSupported();
        }
    }

    /**
     * Constructor of BluetoothCommunicator that adds a callback (it can also be added with addCallback
     *
     * @param context  context
     * @param callback callback added, like in addCallback
     */
    public BluetoothCommunicator(final Context context, Callback callback) {
        this(context);
        addCallback(callback);
    }

    private void initializeConnection() {
        if (bluetoothAdapter != null) {
            BluetoothConnection.Callback connectionCallback = new BluetoothConnection.Callback() {
                @Override
                public void onConnectionRequest(Peer peer) {
                    super.onConnectionRequest(peer);
                    notifyConnectionRequest(peer);
                }

                @Override
                public void onConnectionSuccess(Peer peer, int source) {
                    super.onConnectionSuccess(peer, source);
                    notifyConnectionSuccess(peer, source);
                }

                @Override
                @RequiresPermission(allOf = {
                        "android.permission.BLUETOOTH_ADVERTISE",
                        "android.permission.BLUETOOTH_CONNECT",
                        "android.permission.BLUETOOTH_SCAN"
                })
                public void onConnectionLost(Peer peer) {
                    super.onConnectionLost(peer);
                    synchronized (BluetoothCommunicator.this) {
                        if (connectionServer != null && connectionClient != null) {
                            if (connectionServer.getReconnectingPeers().size() > 0 && !advertising) {
                                executeStartAdvertising();
                            }
                            if (connectionClient.getReconnectingPeers().size() > 0) {
                                if (discovering) {
                                    executeStopDiscovery();
                                    executeStartDiscovery();
                                } else {
                                    executeStartDiscovery();
                                }
                            }
                            notifyConnectionLost(peer);
                        }
                    }
                }

                @Override
                public void onPeerUpdated(Peer peer, Peer newPeer) {
                    super.onPeerUpdated(peer, newPeer);
                    notifyPeerUpdated(peer, newPeer);
                }

                @Override
                @RequiresPermission(allOf = {
                        "android.permission.BLUETOOTH_ADVERTISE",
                        "android.permission.BLUETOOTH_CONNECT",
                        "android.permission.BLUETOOTH_SCAN"
                })
                public void onConnectionResumed(Peer peer) {
                    super.onConnectionResumed(peer);
                    synchronized (BluetoothCommunicator.this) {
                        if (connectionServer != null && connectionClient != null) {
                            if (connectionServer.getReconnectingPeers().size() == 0 && !advertising) {
                                executeStopAdvertising();
                            }
                            if (connectionClient.getReconnectingPeers().size() == 0 && !discovering) {
                                executeStopDiscovery();
                            }
                            notifyConnectionResumed(peer);
                        }
                    }
                }

                @Override
                public void onConnectionFailed(Peer peer, int errorCode) {
                    super.onConnectionFailed(peer, errorCode);
                    notifyConnectionFailed(peer, errorCode);
                }

                @Override
                public void onMessageReceived(Message message, int source) {
                    super.onMessageReceived(message, source);
                    notifyMessageReceived(message, source);
                }

                @Override
                public void onDataReceived(Message data, int source) {
                    super.onMessageReceived(data, source);
                    notifyDataReceived(data, source);
                }

                @Override
                @RequiresPermission(allOf = {
                        "android.permission.BLUETOOTH_ADVERTISE",
                        "android.permission.BLUETOOTH_CONNECT",
                        "android.permission.BLUETOOTH_SCAN"
                })
                public void onDisconnected(Peer peer) {
                    super.onDisconnected(peer);
                    if (connectionServer != null && connectionClient != null) {
                        int peersLeft = connectionServer.getConnectedPeers().size() + connectionClient.getConnectedPeers().size();
                        if (connectionServer.getReconnectingPeers().size() == 0 && !advertising) {
                            executeStopAdvertising();
                        }
                        if (connectionClient.getReconnectingPeers().size() == 0 && !discovering) {
                            executeStopDiscovery();
                        }
                        if (peersLeft == 0) {
                            // reset the queued messages to be sent
                            pendingMessages = new ArrayDeque<>();
                            pendingData = new ArrayDeque<>();
                        }
                        notifyDisconnection(peer, peersLeft);
                    }
                }

                @Override
                @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
                public void onDisconnectionFailed() {
                    super.onDisconnectionFailed();
                    notifyDisconnectionFailed();
                }

            };
            // we create a client that will take care of sending any connection requests and managing those connections
            connectionClient = new BluetoothConnectionClient(context, uniqueName, bluetoothAdapter, strategy, connectionCallback);
            // we create a server that will take care of receiving any connection requests and managing those connections
            connectionServer = new BluetoothConnectionServer(context, uniqueName, bluetoothAdapter, strategy, connectionClient, connectionCallback);
        }
    }


    /**
     * This method start advertising, so this device can be discovered by other devices that use this library and can receive a connection request,
     * this method will eventually turn on bluetooth if it is off (BluetoothCommunicator will restore bluetooth status when both advertising and discovery are stopped)
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STARTED if BluetoothCommunicator is already advertising, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error), DESTROYING if destroy() is called before
     **/
    @RequiresPermission(allOf = {
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT"
    })
    public int startAdvertising() {
        synchronized (bluetoothLock) {
            if (connectionServer == null) {
                initializeConnection();
            }

            if (connectionServer.isGattServerNotInitialized()) {
                connectionServer.initilizeGatServer();
            }

            if (destroying) {
                return DESTROYING;
            }

            if (bluetoothAdapter == null) {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }

            if (Looper.myLooper() != Looper.getMainLooper()) {
                return NOT_MAIN_THREAD;
            }

            if (advertising) {
                return ALREADY_STARTED;
            }

            //start advertising
            int ret = ERROR;
            if (bluetoothAdapter.isEnabled()) {
                if (connectionServer.getReconnectingPeers().size() == 0) {
                    ret = executeStartAdvertising();
                } else {
                    ret = isBluetoothLeSupported();
                }
            } else {
                //turn on bluetooth
                bluetoothAdapter.enable();
            }
            if (ret == SUCCESS) {
                advertising = true;
                notifyAdvertiseStarted();
            }
            return ret;
        }
    }

    @RequiresPermission(allOf = {
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT"
    })
    private int executeStartAdvertising() {
        int advertisementSupportedCode = isBluetoothLeSupported();

        if (advertisementSupportedCode != SUCCESS) {
            return advertisementSupportedCode;
        }

        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        //start advertizing
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)  //alto
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)    //alto
                .setConnectable(true)
                .setTimeout(0)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .addServiceUuid(uuidService)
                .addServiceData(uuidService, uniqueName.getBytes(StandardCharsets.UTF_8))
                .setIncludeDeviceName(true)
                .build();

        BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser != null) {
            advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
            return SUCCESS;
        } else {
            return ERROR;
        }
    }

    /**
     * This method stop advertising, this method will eventually turn off bluetooth if BluetoothCommunicator turned on before and if discovery is off
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STOPPED if BluetoothCommunicator is not advertising, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error)
     **/
    @SuppressLint("MissingPermission")
    public int stopAdvertising() {
        synchronized (bluetoothLock) {
            if (connectionServer == null) {
                initializeConnection();
            }

            if (bluetoothAdapter == null) {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }

            if (Looper.myLooper() != Looper.getMainLooper()) {
                return NOT_MAIN_THREAD;
            }

            if (!advertising) {
                return ALREADY_STOPPED;
            }

            if (connectionServer.isGattServerNotInitialized()) {
                connectionServer.initilizeGatServer();
            }

            //stop advertising
            int ret;
            if (connectionServer.getReconnectingPeers().size() == 0) {
                ret = executeStopAdvertising();
            } else {
                ret = isBluetoothLeSupported();
            }
            if (ret == SUCCESS) {
                advertising = false;
                notifyAdvertiseStopped();
            }
            return ret;
        }
    }

    @RequiresPermission(allOf = {
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT"
    })
    private int executeStopAdvertising() {
        int advertisementSupportedCode = isBluetoothLeSupported();

        if (advertisementSupportedCode != SUCCESS) {
            return advertisementSupportedCode;
        }

        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        if (advertiser == null) {
            return ERROR;
        }

        advertiser.stopAdvertising(advertiseCallback);

        return SUCCESS;
    }

    /**
     * This method will check if BluetoothLe is supported by the device (rarely it can indicate a general bluetooth error, not an incompatibility with bluetooth le)
     *
     * @return SUCCESS if bluetooth le is supported, BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int isBluetoothLeSupported() {
        if (bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported()) {
            return SUCCESS;
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }

    /**
     * This method start discovery so BluetoothCommunicator can discover advertising devices and notify them with onPeerFound,
     * this method will eventually turn on bluetooth if it is off (BluetoothCommunicator will restore bluetooth status when both advertising and discovery are stopped)
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STARTED if BluetoothCommunicator is already advertising, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error), DESTROYING if destroy() is called before
     **/
    @RequiresPermission(allOf = {
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT"
    })
    public int startDiscovery() {
        // If we're already discovering, stop it
        synchronized (bluetoothLock) {
            if (destroying) {
                return DESTROYING;
            }

            if (connectionClient == null) {
                initializeConnection();
            }

            if (bluetoothAdapter == null) {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }

            if (Looper.myLooper() != Looper.getMainLooper()) {
                return NOT_MAIN_THREAD;
            }

            if (discovering) {
                return ALREADY_STARTED;
            }
            //start advertising
            int ret = ERROR;
            if (bluetoothAdapter.isEnabled()) {
                if (connectionClient.getReconnectingPeers().size() == 0) {
                    ret = executeStartDiscovery();
                } else {
                    ret = SUCCESS;
                }
            } else {
                //turn on bluetooth
                bluetoothAdapter.enable();
            }
            if (ret == SUCCESS) {
                discovering = true;
                notifyDiscoveryStarted();
            }
            return ret;
        }
    }

    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    private int executeStartDiscovery() {
        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        ArrayList<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BluetoothConnection.APP_UUID))
                .build());
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST)
                .build();
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();

        if (scanner == null) {
            return ERROR;
        }

        scanner.startScan(scanFilters, scanSettings, discoveryCallback);
        return SUCCESS;
    }

    /**
     * This method stop discovery, this method will eventually turn off bluetooth if BluetoothCommunicator turned on before and if advertising is off
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STOPPED if BluetoothCommunicator is not discovering, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error)
     **/
    @SuppressLint("MissingPermission")
    public int stopDiscovery() {
        synchronized (bluetoothLock) {
            if (bluetoothAdapter == null) {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }

            if (connectionClient == null) {
                initializeConnection();
            }

            if (Looper.myLooper() != Looper.getMainLooper()) {
                return NOT_MAIN_THREAD;
            }

            if (!discovering) {
                return ALREADY_STARTED;
            }

            //stop advertising
            int ret;
            if (connectionClient.getReconnectingPeers().size() == 0) {
                ret = executeStopDiscovery();
            } else {
                ret = SUCCESS;
            }
            if (ret == SUCCESS) {
                discovering = false;
                notifyDiscoveryStopped();
            }

            return ret;
        }
    }

    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    private int executeStopDiscovery() {
        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();

        if (scanner == null) {
            return ERROR;
        }

        scanner.stopScan(discoveryCallback);

        return SUCCESS;
    }

    /**
     * This method will send the text contained in message to the peer contained in the receiver attribute of the message (only if that peer is connected), if receiver is not set
     * the message will be sent to all the connected peers
     *
     * @param message message to be sent
     */
    public void sendMessage(final Message message) {
        mainHandler.post(() -> {
            synchronized (messagesLock) {
                pendingMessages.addLast(message);
                if (pendingMessages.size() == 1) {  // if it is true then we are not writing any messages
                    sendMessage();
                }
            }
        });
    }

    private void sendMessage() {
        if (connectionClient != null && connectionServer != null) {
            final Message message = pendingMessages.peekFirst();
            if (message != null) {
                connectionClient.sendMessage(message, new Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        connectionServer.sendMessage(message, new Channel.MessageCallback() {
                            @Override
                            public void onMessageSent() {   // means that we have sent the message to all the client and server channels
                                pendingMessages.pollFirst();  // remove the newly sent ConversationMessage
                                sendMessage();  // send any other messages
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * This method will send the data contained in message to the peer contained in the receiver attribute of the message (only if that peer is connected), if receiver is not set
     * the message will be sent to all the connected peers
     *
     * @param data message to be sent
     */
    public void sendData(final Message data) {
        mainHandler.post(() -> {
            synchronized (dataLock) {
                pendingData.addLast(data);
                if (pendingData.size() == 1) {  // if it is true then we are not writing any messages
                    sendData();
                }
            }
        });
    }

    private void sendData() {
        if (connectionClient != null && connectionServer != null) {
            final Message data = pendingData.peekFirst();
            if (data != null) {
                connectionClient.sendData(data, new Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        connectionServer.sendData(data, new Channel.MessageCallback() {
                            @Override
                            public void onMessageSent() {   // means that we have sent the message to all the client and server channels
                                pendingData.pollFirst();  // remove the newly sent ConversationMessage
                                sendData();  // send any other messages
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * This method will return the list of all connected peers
     *
     * @return list of connected peers
     */
    public ArrayList<Peer> getConnectedPeersList() {
        ArrayList<Peer> connectedPeers = new ArrayList<>();
        if (connectionServer != null && connectionClient != null) {
            connectedPeers.addAll(connectionServer.getConnectedPeers());
            connectedPeers.addAll(connectionClient.getConnectedPeers());
        }
        return connectedPeers;
    }

    /**
     * This method must be used after you have received a connection request to accept it and complete the connection (the connection is complete when
     * onConnectionSuccess is called)
     *
     * @param peer the peer that has sent the connection request that we want to accept
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public int acceptConnection(Peer peer) {
        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        if (connectionServer == null) {
            initializeConnection();
        }

        if (connectionServer.isGattServerNotInitialized()) {
            connectionServer.initilizeGatServer();
        }

        connectionServer.acceptConnection((Peer) peer.clone());

        return SUCCESS;
    }

    /**
     * This method must be used after you have received a connection request to reject it and cancel the connection.
     *
     * @param peer the peer that has sent the connection request that you want to accept
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public int rejectConnection(Peer peer) {
        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        if (connectionServer == null) {
            initializeConnection();
        }

        if (connectionServer.isGattServerNotInitialized()) {
            connectionServer.initilizeGatServer();
        }

        connectionServer.rejectConnection((Peer) peer.clone());

        return SUCCESS;
    }

    /**
     * This method is used to know if BluetoothCommunicator is advertising
     *
     * @return advertising
     */
    public boolean isAdvertising() {
        return advertising;
    }

    /**
     * This method is used to know if BluetoothCommunicator is discovering
     *
     * @return discovering
     */
    public boolean isDiscovering() {
        return discovering;
    }

    /**
     * This method must be used to send a connection request to a founded peer, successfully you can know if it has accepted or rejected the connection listening
     * onConnectionSuccess and onConnectionFailed
     *
     * @param peer found peer you want to connect with
     * @return SUCCESS if everything is gone OK, BLUETOOTH_LE_NOT_SUPPORTED if bluetooth le is not supported (or rarely if we had a generic bluetooth problem)
     * or DESTROYING if destroy() is called before
     */
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public int connect(final Peer peer) {
        if (destroying) {
            return DESTROYING;
        }

        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        if (connectionServer == null || connectionClient == null) {
            initializeConnection();
        }

        if (connectionServer.isGattServerNotInitialized()) {
            connectionServer.initilizeGatServer();
        }

        if (connectionClient != null) {
            connectionClient.connect((Peer) peer.clone());
        }

        return SUCCESS;
    }

    public int readPhy(Peer peer) {
        if (connectionClient == null || connectionServer == null) {
            initializeConnection();
        }

        if (bluetoothAdapter == null) {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }

        assert connectionServer != null;

        connectionServer.readPhy((Peer) peer.clone());
        connectionClient.readPhy((Peer) peer.clone());

        return SUCCESS;
    }

    /**
     * This method return the bluetooth adapter used by BluetoothCommunicator
     *
     * @return bluetoothAdapter
     */
    @Nullable
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /**
     * This method disconnect the peer passed to the argument (it must be connected or nothing happens), the disconnection is completed
     * when onDisconnected is called with that peer as argument, in case the disconnection fails onDisconnectionFailed is called but if you not
     * override it or leave the call to super BluetoothCommunicator will turn off and on bluetooth to force the disconnection and onDisconnection will be
     * called.
     *
     * @param peer connected peer you want to disconnect from
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int disconnect(final Peer peer) {
        synchronized (bluetoothLock) {
            if (connectionClient == null || connectionServer == null) {
                initializeConnection();
            }

            if (bluetoothAdapter == null) {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }

            assert connectionServer != null;

            connectionServer.disconnect((Peer) peer.clone());
            connectionClient.disconnect((Peer) peer.clone());

            return SUCCESS;
        }
    }

    /**
     * This method will call disconnect for all the connected peers
     *
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int disconnectFromAll() {
        if (connectionClient == null || connectionServer == null) {
            initializeConnection();
        }
        if (connectionClient != null && connectionServer != null) {
            connectionServer.disconnectAll(new Channel.DisconnectionNotificationCallback() {
                @Override
                public void onDisconnectionNotificationSent() {
                    connectionClient.disconnectAll();
                }
            });
            return SUCCESS;
        }
        return BLUETOOTH_LE_NOT_SUPPORTED;
    }

    /**
     * This method must be called when you not use anymore BluetoothCommunicator for release resources, in the case you had saved BluetoothCommunicator in Global and/or
     * you will use BluetoothCommunicator for the entire life of application you can avoid the call to this method
     */
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void destroy() {
        if (bluetoothAdapter != null && connectionClient != null && connectionServer != null) {
            destroying = true;
            connectionClient.destroy();
            connectionServer.destroy();
        }
    }


    /**
     * With this method you can add the callback for listening all the events of BluetoothCommunicator
     *
     * @param callback callback for listening all the events of BluetoothCommunicator
     */
    public void addCallback(Callback callback) {
        clientCallbacks.add(callback);
    }

    /**
     * This remote will remove the callback you pass as argument from the list of callback of this class, so this callback will not receive method call anymore
     *
     * @param callback callback for listening all the events of BluetoothCommunicator
     */
    public void removeCallback(Callback callback) {
        clientCallbacks.remove(callback);
    }


    private void notifyAdvertiseStarted() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onAdvertiseStarted();
        }
    }

    private void notifyDiscoveryStarted() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onDiscoveryStarted();
        }
    }

    private void notifyAdvertiseStopped() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onAdvertiseStopped();
        }
    }

    private void notifyDiscoveryStopped() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onDiscoveryStopped();
        }
    }

    private void notifyPeerFound(final Peer peer) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onPeerFound(peer);
            }
        });
    }

    private void notifyPeerLost(final Peer peer) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onPeerLost(peer);
            }
        });
    }

    private void notifyConnectionRequest(final Peer peer) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onConnectionRequest(peer);
            }
        });
    }

    private void notifyConnectionSuccess(final Peer peer, final int source) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onConnectionSuccess(peer, source);
            }
        });
    }

    private void notifyConnectionFailed(final Peer peer, final int errorCode) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onConnectionFailed(peer, errorCode);
            }
        });
    }

    private void notifyConnectionResumed(final Peer peer) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onConnectionResumed(peer);
            }
        });
    }

    private void notifyConnectionLost(final Peer peer) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onConnectionLost(peer);
            }
        });
    }

    private void notifyMessageReceived(final Message message, final int source) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onMessageReceived(message, source);
            }
        });
    }

    private void notifyDataReceived(final Message data, final int source) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onDataReceived(data, source);
            }
        });
    }

    private void notifyPeerUpdated(final Peer peer, final Peer newPeer) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onPeerUpdated(peer, newPeer);
            }
        });
    }

    private void notifyDisconnection(final Peer peer, final int peersLeft) {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onDisconnected(peer, peersLeft);
            }
        });
    }

    private void notifyDisconnectionFailed() {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onDisconnectionFailed();
            }
        });
    }

    private void notifyBluetoothLeNotSupported() {
        mainHandler.post(() -> {
            for (int i = 0; i < clientCallbacks.size(); i++) {
                clientCallbacks.get(i).onBluetoothLeNotSupported();
            }
        });
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public String getUniqueName() {
        assert bluetoothAdapter != null;
        return bluetoothAdapter.getName() + uniqueName;
    }

    public static abstract class Callback extends BluetoothConnection.Callback {
        /**
         * Notify that advertise has started, if you want to do something after the start of advertising do it here, because
         * after startAdvertise there is no guarantee that advertise is really started (it is delayed)
         */
        public void onAdvertiseStarted() {
        }

        /**
         * Notify that discovery has started, if you want to do something after the start of discovery do it here, because
         * after startDiscovery there is no guarantee that discovery is really started (it is delayed)
         */
        public void onDiscoveryStarted() {
        }

        /**
         * Notify that advertise has stopped, if you want to do something after the stop of advertising do it here, because
         * after stopAdvertising there is no guarantee that advertise is really stopped (it is delayed)
         */
        public void onAdvertiseStopped() {
        }

        /**
         * Notify that discovery has stopped, if you want to do something after the stop of discovery do it here, because
         * after stopDiscovery there is no guarantee that discovery is really stopped (it is delayed)
         */
        public void onDiscoveryStopped() {
        }

        /**
         * Notify that a Peer is found with the discovery.
         * <br /><br />
         * Here for example you can save peer in a list or anywhere you want and when the user
         * choose a peer you can call bluetoothCommunicator.connect(peer founded) but if you want to
         * use a peer for connect you have to have peer updated (see onPeerUpdated or onPeerLost), if you use a
         * non updated peer the connection might fail
         * instead if you want to immediate connect where peer is found you can call bluetoothCommunicator.connect(peer) here.
         *
         * @param peer founded peer
         */
        public void onPeerFound(Peer peer) {
        }

        /**
         * Notify that a peer is out of range or has interrupted the advertise.
         * <br /><br />
         *
         * @param peer Here you can delete the peer lost from a eventual collection of founded peers.
         */
        public void onPeerLost(Peer peer) {
        }

        /**
         * Notify that a peer is disconnected, peersLeft indicate the number of connected peers remained
         *
         * @param peer      disconnected peer
         * @param peersLeft remaining peers connected
         */
        public void onDisconnected(Peer peer, int peersLeft) {
        }

        /**
         * notify that bluetooth low energy is not compatible with this device (that for now is never been called)
         */
        public void onBluetoothLeNotSupported() {
        }
    }
}
