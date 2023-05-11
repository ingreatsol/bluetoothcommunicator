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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.ingreatsol.bluetoothcommunicator.tools.BluetoothTools;
import com.ingreatsol.bluetoothcommunicator.tools.Timer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;


class BluetoothConnectionServer extends BluetoothConnection {
    //costants
    public static final String TAG = "BluetoothConnectionServer";
    public static final UUID CONNECTION_REQUEST_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0857350c7a66");
    public static final UUID CONNECTION_RESPONSE_UUID = UUID.fromString("fa87c0d0-adac-11de-8a39-0857350c7a64");
    public static final UUID CONNECTION_RESUMED_SEND_UUID = UUID.fromString("fa87c0d1-acbc-11de-8e32-0857350c7a41");
    public static final UUID CONNECTION_RESUMED_RECEIVE_UUID = UUID.fromString("fa87c0d1-afac-11de-8a39-0857350c7a43");
    public static final UUID MTU_REQUEST_UUID = UUID.fromString("fa87c0d4-afac-11de-8a39-0857350c7a60");
    public static final UUID MTU_RESPONSE_UUID = UUID.fromString("fa87c0d5-adac-11de-8a39-0857350c7a61");
    public static final UUID MESSAGE_SEND_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0830350c9a66");
    public static final UUID DATA_SEND_UUID = UUID.fromString("fa87c0d0-afac-11de-8a33-0830350c9a66");
    public static final UUID MESSAGE_RECEIVE_UUID = UUID.fromString("fa87c0d0-afac-11dc-8a39-0850350c8a66");
    public static final UUID DATA_RECEIVE_UUID = UUID.fromString("fa87c0d0-afac-11dd-8a32-0850350c8a66");
    public static final UUID READ_RESPONSE_MESSAGE_RECEIVED_UUID = UUID.fromString("fa87c0d0-aaac-11df-8a38-0897350c8a60");
    public static final UUID READ_RESPONSE_DATA_RECEIVED_UUID = UUID.fromString("fa87c0d0-aaac-11df-8a38-0897350c8f65");
    public static final UUID NAME_UPDATE_SEND_UUID = UUID.fromString("fa87c0d4-afab-11de-8a39-0857350c7a42");
    public static final UUID NAME_UPDATE_RECEIVE_UUID = UUID.fromString("fa87c0d1-afab-11de-8a39-0857350c7a40");
    public static final UUID DISCONNECTION_SEND_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0897350c5a66");
    public static final UUID DISCONNECTION_RECEIVE_UUID = UUID.fromString("fa87c0d0-adac-11de-8a38-0897350c5a65");
    //objects
    private BluetoothGattServer bluetoothGattServer;
    private final BluetoothManager bluetoothManager;
    private final BluetoothConnectionClient client;
    private final Context context;

    public BluetoothConnectionServer(@NonNull final Context context,
                                     final String name,
                                     @NonNull final BluetoothAdapter bluetoothAdapter,
                                     final int strategy,
                                     final BluetoothConnectionClient client,
                                     final Callback callback) {
        super(name, bluetoothAdapter, strategy, callback);

        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.client = client;
        this.context = context;
    }

    public boolean isGattServerNotInitialized() {
        return bluetoothGattServer == null;
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void initilizeGatServer() {
        initializeGattServer(client);
        addBluetoothGattService();
        Log.d(TAG, "Initialized GattServer");
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private void initializeGattServer(final BluetoothConnectionClient client) {
        bluetoothGattServer = bluetoothManager.openGattServer(context, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, final int newState) {
                super.onConnectionStateChange(device, status, newState);
                onChangeConnectionState(device, newState, client);
            }

            @Override
            @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
            public synchronized void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId,
                                                                  final BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                                                                  boolean responseNeeded, final int offset, final byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                onRequestCharacteristicWrite(device, requestId, characteristic, offset, value);
            }

            @Override
            @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
            public void onCharacteristicReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                onRequestCharacteristicRead(device, requestId, offset, characteristic);
            }

            @Override
            public void onNotificationSent(final BluetoothDevice device, final int status) {
                super.onNotificationSent(device, status);
                onSentNotification(device, status);
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                super.onMtuChanged(device, mtu);
            }

            @Override
            public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
                super.onPhyUpdate(device, txPhy, rxPhy, status);
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                super.onServiceAdded(status, service);
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                super.onExecuteWrite(device, requestId, execute);
            }

            @Override
            public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
                super.onPhyRead(device, txPhy, rxPhy, status);
            }
        });
    }

    private void onSentNotification(final BluetoothDevice device, final int status) {
        mainHandler.post(() -> {
            synchronized (channelsLock) {
                int index = channels.indexOf(new Peer(device, null, true));

                if (index != -1) {
                    UUID sendingCharacteristic = ((ServerChannel) channels.get(index)).getSendingCharacteristic();
                    if (CONNECTION_RESPONSE_UUID.equals(sendingCharacteristic)) {
                        if (!channels.get(index).getPeer().isDisconnecting()) {
                            notifyConnectionSuccess(channels.get(index));
                        }

                    } else if (CONNECTION_RESUMED_SEND_UUID.equals(sendingCharacteristic)) {
                        if (!channels.get(index).getPeer().isDisconnecting()) {
                            //connection resumed
                            notifyConnectionResumed(channels.get(index));
                        }

                    } else if (MESSAGE_SEND_UUID.equals(sendingCharacteristic)) {
                        if (status == BluetoothGatt.GATT_FAILURE) {
                            channels.get(index).onSubMessageWriteFailed();
                        }

                    } else if (DATA_SEND_UUID.equals(sendingCharacteristic)) {
                        if (status == BluetoothGatt.GATT_FAILURE) {
                            channels.get(index).onSubDataWriteFailed();
                        }

                    } else if (DISCONNECTION_SEND_UUID.equals(sendingCharacteristic)) {
                        channels.get(index).disconnect(disconnectionCallback);

                    }
                }
            }
        });
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private void onRequestCharacteristicRead(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattCharacteristic characteristic) {
        mainHandler.post(() -> {
            synchronized (channelsLock) {
                int index = channels.indexOf(new Peer(device, null, true));

                if (index != -1) {
                    try {
                        BluetoothMessage pendingSubData = channels.get(index).getPendingSubData();
                        if (pendingSubData != null) {     // if pendingSubData is null or does not match it means that the message has already been confirmed, or has yet to be confirmed, but we do nothing because this is only a repetition of a previous confirmation
                            if (DATA_SEND_UUID.equals(characteristic.getUuid())) {
                                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, pendingSubData.getCompleteData());
                            } else {
                                throw new Exception();
                            }
                        }
                    } catch (Exception e) {
                        bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                    }
                }
            }
        });
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private void addBluetoothGattService() {
        BluetoothGattService service = new BluetoothGattService(BluetoothConnection.APP_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        service.addCharacteristic(new BluetoothGattCharacteristic(CONNECTION_REQUEST_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(CONNECTION_RESPONSE_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(CONNECTION_RESUMED_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(CONNECTION_RESUMED_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(MTU_REQUEST_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(MTU_RESPONSE_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(MESSAGE_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(DATA_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(MESSAGE_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(DATA_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(READ_RESPONSE_MESSAGE_RECEIVED_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(READ_RESPONSE_DATA_RECEIVED_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(NAME_UPDATE_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        service.addCharacteristic(new BluetoothGattCharacteristic(NAME_UPDATE_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(DISCONNECTION_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(DISCONNECTION_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));

        bluetoothGattServer.addService(service);
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private void onRequestCharacteristicWrite(final BluetoothDevice device, final int requestId,
                                              final BluetoothGattCharacteristic characteristic,
                                              final int offset, final byte[] value) {
        mainHandler.post(() -> {
            synchronized (channelsLock) {
                int index = channels.indexOf(new Peer(device, null, true));

                if (characteristic.getUuid().equals(CONNECTION_REQUEST_UUID)) {
                    if (index != -1) {
                        if (!channels.get(index).getPeer().isDisconnecting()) {
                            if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isReconnecting()) {
                                String data = new String(value, StandardCharsets.UTF_8);
                                channels.get(index).getPeer().setUniqueName(data);

                                notifyConnectionRequest(channels.get(index));
                                bluetoothGattServer.sendResponse(device, requestId, ACCEPT, offset, null);

                            } else if (channels.get(index).getPeer().isReconnecting()) {
                                bluetoothGattServer.sendResponse(device, requestId, REJECT, offset, null);  // it must be put before the disconnection otherwise we have errors
                                stopReconnection(channels.get(index));

                            } else {
                                bluetoothGattServer.sendResponse(device, requestId, ACCEPT, offset, null);
                            }
                        } else {
                            bluetoothGattServer.sendResponse(device, requestId, REJECT, offset, null);
                        }
                    }

                } else if (characteristic.getUuid().equals(CONNECTION_RESUMED_RECEIVE_UUID)) {
                    if (index != -1) {
                        if (!channels.get(index).getPeer().isDisconnecting()) {
                            if (channels.get(index).getPeer().isReconnecting()) {
                                if (!((ServerChannel) channels.get(index)).notifyConnectionResumed()) {
                                    stopReconnection(channels.get(index));
                                }
                            } else if (!channels.get(index).getPeer().isConnected()) {
                                        /* means that the peer for which we accepted the connection request without having it in the list of channels is not starting a connection but is resetting it,
                                         but we are not, so we disconnect, otherwise we would remain forever waiting for the connection request */
                                channels.get(index).getPeer().setDisconnecting(true);
                                if (!((ServerChannel) channels.get(index)).notifyConnectionResumedRejected()) {
                                    channels.get(index).disconnect(disconnectionCallback);
                                }
                            }
                        }
                    }

                } else if (characteristic.getUuid().equals(MTU_REQUEST_UUID)) {
                    if (index != -1) {
                        if (!channels.get(index).getPeer().isDisconnecting()) {
                            try {
                                int mtu = value.length;
                                BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);
                                BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.MTU_RESPONSE_UUID);

                                output.setValue(String.valueOf(mtu).getBytes(StandardCharsets.UTF_8));
                                bluetoothGattServer.notifyCharacteristicChanged(channels.get(index).getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                            } catch (Exception e) {
                                channels.get(index).disconnect(disconnectionCallback);
                            }
                        }
                    }

                } else if (characteristic.getUuid().equals(MESSAGE_RECEIVE_UUID)) {
                    if (index != -1) {
                        Peer sender = (Peer) channels.get(index).getPeer().clone();
                        BluetoothMessage subMessage = BluetoothMessage.createFromBytes(sender, value);
                        if (subMessage != null) {
                            if (!channels.get(index).getReceivedMessages().contains(subMessage)) {
                                int messageIndex = channels.get(index).getReceivingMessages().indexOf(subMessage);
                                if (messageIndex == -1) {
                                    channels.get(index).getReceivingMessages().add(subMessage);
                                    messageIndex = channels.get(index).getReceivingMessages().size() - 1;
                                } else {
                                    channels.get(index).getReceivingMessages().get(messageIndex).addMessage(subMessage);
                                }
                                if (subMessage.getType() == BluetoothMessage.FINAL) {
                                    BluetoothMessage bluetoothMessage = channels.get(index).getReceivingMessages().remove(messageIndex);
                                    Message message = bluetoothMessage.convertInMessage();
                                    channels.get(index).addReceivedMessage(bluetoothMessage);
                                    if (message != null) {
                                        notifyMessageReceived(message);
                                        assert message.getSender() != null;
                                        assert message.getSender().getDevice() != null;
                                        Log.e("clientMessageReceive", message.getText() + "-" + message.getSender().getDevice().getAddress());
                                    }
                                }
                            }
                            //response
                            byte[] responseData = BluetoothTools.concatBytes(subMessage.getId().getValue().getBytes(StandardCharsets.UTF_8), subMessage.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData);
                        }
                    }
                } else if (characteristic.getUuid().equals(DATA_RECEIVE_UUID)) {
                    if (index != -1) {
                        Peer sender = (Peer) channels.get(index).getPeer().clone();
                        BluetoothMessage subData = BluetoothMessage.createFromBytes(sender, value);
                        if (subData != null) {
                            if (!channels.get(index).getReceivedData().contains(subData)) {
                                int dataIndex = channels.get(index).getReceivingData().indexOf(subData);
                                if (dataIndex == -1) {
                                    channels.get(index).getReceivingData().add(subData);
                                    dataIndex = channels.get(index).getReceivingData().size() - 1;
                                } else {
                                    channels.get(index).getReceivingData().get(dataIndex).addMessage(subData);
                                }
                                if (subData.getType() == BluetoothMessage.FINAL) {
                                    BluetoothMessage bluetoothMessage = channels.get(index).getReceivingData().remove(dataIndex);
                                    Message message = bluetoothMessage.convertInMessage();
                                    channels.get(index).addReceivedData(bluetoothMessage);
                                    if (message != null) {
                                        notifyDataReceived(message);
                                        assert message.getSender() != null;
                                        assert message.getSender().getDevice() != null;
                                        Log.e("clientDataReceive", message.getText() + "-" + message.getSender().getDevice().getAddress());
                                    }
                                }
                            }
                            //response
                            byte[] responseData = BluetoothTools.concatBytes(subData.getId().getValue().getBytes(StandardCharsets.UTF_8), subData.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData);
                        }
                    }

                } else if (characteristic.getUuid().equals(READ_RESPONSE_MESSAGE_RECEIVED_UUID)) {
                    int totalLength = BluetoothMessage.ID_LENGTH + BluetoothMessage.SEQUENCE_NUMBER_LENGTH;
                    String completeText = new String(value, StandardCharsets.UTF_8);
                    if (completeText.length() >= totalLength) {
                        BluetoothMessage.SequenceNumber id = new BluetoothMessage.SequenceNumber(completeText.substring(0, BluetoothMessage.ID_LENGTH), BluetoothMessage.ID_LENGTH);
                        BluetoothMessage.SequenceNumber sequenceNumber = new BluetoothMessage.SequenceNumber(completeText.substring(BluetoothMessage.ID_LENGTH, totalLength), BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
                        BluetoothMessage pendingSubMessage = channels.get(index).getPendingSubMessage();
                        // if pendingSubMessage is null or does not match it means that the message has already been confirmed, or has yet to be confirmed, but we do nothing because this is only a repetition of a previous confirmation
                        if (pendingSubMessage != null && id.equals(pendingSubMessage.getId()) && sequenceNumber.equals(pendingSubMessage.getSequenceNumber())) {
                            channels.get(index).onSubMessageWriteSuccess();
                        }

                    }

                } else if (characteristic.getUuid().equals(READ_RESPONSE_DATA_RECEIVED_UUID)) {
                    int totalLength = BluetoothMessage.ID_LENGTH + BluetoothMessage.SEQUENCE_NUMBER_LENGTH;
                    String completeText = new String(value, StandardCharsets.UTF_8);
                    if (completeText.length() >= totalLength) {
                        BluetoothMessage.SequenceNumber id = new BluetoothMessage.SequenceNumber(completeText.substring(0, BluetoothMessage.ID_LENGTH), BluetoothMessage.ID_LENGTH);
                        BluetoothMessage.SequenceNumber sequenceNumber = new BluetoothMessage.SequenceNumber(completeText.substring(BluetoothMessage.ID_LENGTH, totalLength), BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
                        BluetoothMessage pendingSubData = channels.get(index).getPendingSubData();
                        // if pendingSubData is null or does not match it means that the message has already been confirmed, or has yet to be confirmed, but we do nothing because this is only a repetition of a previous confirmation
                        if (pendingSubData != null && id.equals(pendingSubData.getId()) && sequenceNumber.equals(pendingSubData.getSequenceNumber())) {
                            channels.get(index).onSubDataWriteSuccess();
                        }

                    }

                } else if (characteristic.getUuid().equals(NAME_UPDATE_RECEIVE_UUID)) {
                    if (index != -1) {
                        Peer newPeer = (Peer) channels.get(index).getPeer().clone();
                        newPeer.setUniqueName(new String(value, StandardCharsets.UTF_8));
                        notifyPeerUpdated(channels.get(index), newPeer);
                    }

                } else if (characteristic.getUuid().equals(DISCONNECTION_RECEIVE_UUID)) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                    if (index != -1) {
                        channels.get(index).disconnect(disconnectionCallback);
                    }
                }
            }
        });
    }

    private void onChangeConnectionState(BluetoothDevice device, final int newState, final BluetoothConnectionClient client) {
        final Peer peer = new Peer(device, null, false);
        //anche se non serve si mette solo per questioni di simmetria col server a livello programmatico
        mainHandler.post(() -> {
            if (newState == BluetoothProfile.STATE_CONNECTED) {

                synchronized (channelsLock) {
                    int index;
                    if (!client.getConnectedPeers().contains(peer) && !channels.contains(peer)) {   // the client object is used to manage synchronization with the client to avoid adding a device that connects to the latter instead of us
                        channels.add(new ServerChannel(peer, bluetoothAdapter));
                        index = channels.size() - 1;
                        ((ServerChannel) channels.get(index)).setBluetoothGattServer(bluetoothGattServer);
                        channels.get(index).getPeer().setHardwareConnected(true);

                    } else {
                        index = channels.indexOf(peer);
                        if (index != -1) {
                            ((ServerChannel) channels.get(index)).setBluetoothGattServer(bluetoothGattServer);
                            channels.get(index).getPeer().setHardwareConnected(true);
                            if (channels.get(index).getPeer().isReconnecting()) {
                                // the connection is recovering so we reset the timer, so in case of failure we will still have a disconnection
                                channels.get(index).resetReconnectionTimer();
                            }
                        }

                    }
                    if (index != -1) {
                        final Channel channel = channels.get(index);
                        channel.startConnectionCompleteTimer(new Timer.Callback() {
                            @Override
                            public void onFinished() {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // means that the connection failed because it did not happen completely by the end of the timer
                                        if (channel.getPeer().isReconnecting()) {
                                            stopReconnection(channel);
                                        } else {
                                            channel.disconnect(disconnectionCallback);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized (channelsLock) {
                    final int index = channels.indexOf(peer);
                    if (index != -1) {
                        ((ServerChannel) channels.get(index)).setBluetoothGattServer(null);
                        channels.get(index).getPeer().setHardwareConnected(false);

                        if (channels.get(index).getPeer().isDisconnecting()) {
                            channels.get(index).onDisconnected();
                        }

                        if (channels.get(index).getPeer().isConnected()) {
                            if (channels.get(index).getPeer().isDisconnecting()) {
                                // disconnection
                                notifyDisconnection(channels.get(index));

                            } else {
                                // connection lost
                                notifyConnectionLost(channels.get(index));
                            }
                        } else {
                            if (channels.get(index).getPeer().isReconnecting()) {
                                if (channels.get(index).getPeer().isDisconnecting()) {
                                    // we had a disconnection after the stopReconnection call (due to the latter method)
                                    notifyDisconnection(channels.get(index));

                                } else {
                                    // we had a disconnect between the hw connection and the complete connection
                                    stopReconnection(channels.get(index));

                                }

                            } else {
                                // means that there has been a disconnect between the connection request and its acceptance.
                                // we delete the disconnected channel
                                channels.remove(index);
                            }
                        }
                    }
                }
            }
        });
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void acceptConnection(final Peer peer) {
        mainHandler.post(() -> {
            synchronized (channelsLock) {
                int index = channels.indexOf(peer);
                if (index != -1) {
                    if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isReconnecting()) {
                        if (!((ServerChannel) channels.get(index)).acceptConnection()) {
                            channels.get(index).disconnect(disconnectionCallback);
                        }
                    }
                }
            }
        });
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void rejectConnection(final Peer peer) {
        mainHandler.post(() -> {
            synchronized (channelsLock) {
                int index = channels.indexOf(peer);
                if (index != -1) {
                    channels.get(index).resetConnectionCompleteTimer();
                    if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isReconnecting()) {
                        channels.get(index).getPeer().setDisconnecting(true);
                        if (!((ServerChannel) channels.get(index)).rejectConnection()) {
                            channels.get(index).disconnect(disconnectionCallback);
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void stopReconnection(@NonNull final Channel channel) {
        channel.resetConnectionCompleteTimer();
        channel.resetReconnectionTimer();   // if it has not been called since the timer has expired

        channel.disconnect(new Channel.DisconnectionCallback() {
            @Override
            public void onAlreadyDisconnected(Peer peer) {
                notifyDisconnection(channel);
            }

            @Override
            public void onDisconnectionFailed() {
                disconnectionCallback.onDisconnectionFailed();
            }
        });    // to cancel a possible connection in progress
    }

    @Override
    public void readPhy(final Peer peer) {
        mainHandler.post(() -> {
            synchronized (channelsLock) {
                int index = channels.indexOf(peer);
                if (index != -1) {
                    channels.get(index).readPhy();
                }
            }
        });
    }

    public void updateName(final String uniqueName) {
        mainHandler.post(() -> {
            synchronized (channelsLock) {
                setUniqueName(uniqueName);
                for (Channel channel : channels) {
                    channel.notifyNameUpdated(uniqueName);
                }
            }
        });
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void close() {
        bluetoothGattServer.close();
    }

    private void notifyConnectionRequest(@NonNull Channel channel) {
        callback.onConnectionRequest((Peer) channel.getPeer().clone());
    }

    @Override
    protected void notifyConnectionSuccess(@NonNull Channel channel) {
        channel.resetConnectionCompleteTimer();
        channel.getPeer().setConnected(true);
        callback.onConnectionSuccess((Peer) channel.getPeer().clone(), BluetoothCommunicator.SERVER);
    }

    @Override
    protected void notifyMessageReceived(Message message) {
        callback.onMessageReceived(message, BluetoothCommunicator.SERVER);
    }

    @Override
    protected void notifyDataReceived(Message data) {
        callback.onDataReceived(data, BluetoothCommunicator.SERVER);
    }

    @Override
    protected void notifyConnectionLost(@NonNull final Channel channel) {
        channel.getPeer().setReconnecting(true, false);
        callback.onConnectionLost((Peer) channel.getPeer().clone());
        channel.startReconnectionTimer(new Timer.Callback() {
            @Override
            public void onFinished() {
                // reconnection failed
                stopReconnection(channel);
            }
        });
    }

    @Override
    protected void notifyConnectionResumed(@NonNull Channel channel) {
        channel.resetConnectionCompleteTimer();
        int index = channels.indexOf(channel);
        if (index != -1) {
            channel.getPeer().setReconnecting(false, true);
            channels.set(index, channel);
            callback.onConnectionResumed((Peer) channels.get(index).getPeer().clone());
        }
    }

    @Override
    protected void notifyPeerUpdated(@NonNull Channel channel, Peer newPeer) {
        Peer peerClone = (Peer) channel.getPeer().clone();
        channel.setPeer(newPeer);
        callback.onPeerUpdated(peerClone, newPeer);
    }

    @Override
    protected void notifyDisconnection(Channel channel) {
        channels.remove(channel);
        channel.getPeer().setReconnecting(false, false);  // we also set the reconnecting to false in case we were reconnecting before the disconnection took place
        callback.onDisconnected((Peer) channel.getPeer().clone());
    }
}
