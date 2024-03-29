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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ingreatsol.bluetoothcommunicator.tools.BluetoothTools;

import org.jetbrains.annotations.Contract;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

class BluetoothMessage implements Parcelable {
    public static final int ID_LENGTH = 4;
    public static final int SEQUENCE_NUMBER_LENGTH = 3;
    public static final int TYPE_LENGTH = 1;
    public static final int TOTAL_LENGTH = ID_LENGTH + SEQUENCE_NUMBER_LENGTH + TYPE_LENGTH;
    public static final int NON_FINAL = 1;
    public static final int FINAL = 2;
    private Peer sender;  // if we are the sender, the sender can be null
    private SequenceNumber id;
    private SequenceNumber sequenceNumber;
    private int type;
    private byte[] data;

    public BluetoothMessage(Peer sender, SequenceNumber id, SequenceNumber sequenceNumber, int type, byte[] data) {
        this.sender = sender;
        this.id = id;
        this.sequenceNumber = sequenceNumber;
        this.type = type;
        this.data = data;
    }

    public BluetoothMessage(SequenceNumber id, SequenceNumber sequenceNumber, int type, byte[] data) {
        this.id = id;
        this.sequenceNumber = sequenceNumber;
        this.type = type;
        this.data = data;
    }

    @Nullable
    public static BluetoothMessage createFromBytes(Peer sender, byte[] completeData) {
        String completeText = new String(completeData, StandardCharsets.UTF_8);
        if (completeText.length() > TOTAL_LENGTH) {
            SequenceNumber id = new SequenceNumber(completeText.substring(0, ID_LENGTH), ID_LENGTH);
            SequenceNumber sequenceNumber = new SequenceNumber(completeText.substring(ID_LENGTH, ID_LENGTH + SEQUENCE_NUMBER_LENGTH), SEQUENCE_NUMBER_LENGTH);
            int type = Integer.parseInt(completeText.substring(ID_LENGTH + SEQUENCE_NUMBER_LENGTH, TOTAL_LENGTH));
            byte[] data = BluetoothTools.subBytes(completeData, TOTAL_LENGTH, completeData.length);   // the header is deleted
            if (data != null && sender != null) {
                return new BluetoothMessage(sender, id, sequenceNumber, type, data);
            }
        }
        return null;
    }


    public SequenceNumber getId() {
        return id;
    }

    public void setId(SequenceNumber id) {
        this.id = id;
    }

    public SequenceNumber getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(SequenceNumber sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Peer getSender() {
        return sender;
    }

    public void setSender(Peer sender) {
        this.sender = sender;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void addMessage(@NonNull BluetoothMessage message) {
        if (this.equals(message)) {
            if (getSequenceNumber() != null && message.getSequenceNumber() != null && message.getSequenceNumber().compare(getSequenceNumber()) > 0) {
                this.data = BluetoothTools.concatBytes(this.data, message.getData());
                setSequenceNumber(message.getSequenceNumber());
                type = message.getType();
            }
        }
    }

    public byte[] getCompleteData() {
        return BluetoothTools.concatBytes(getId().getValue().getBytes(StandardCharsets.UTF_8),
                getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8),
                String.valueOf(getType()).getBytes(StandardCharsets.UTF_8),
                getData());
    }

    public Message convertInMessage() {
        String completeText = new String(data, StandardCharsets.UTF_8);
        if (completeText.length() > 0) {
            String header = completeText.substring(0, Message.HEADER_LENGTH);
            byte[] data = BluetoothTools.subBytes(getData(), header.getBytes(StandardCharsets.UTF_8).length, getData().length);
            if (data != null) {
                return new Message(sender, header, data);
            }
        }
        return null;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof BluetoothMessage) {
            BluetoothMessage message = (BluetoothMessage) obj;
            if (sender != null && message.getSender() != null) {
                if (sender.equals(message.getSender())) {
                    if (id != null && message.getId() != null) {
                        return id.equals(message.getId());
                    }
                }
            } else {
                return false;
            }
        }

        return false;
    }

    public static class SequenceNumber {
        private final int size;
        private final ArrayList<Character> supportedUTFCharacters;
        private final Character[] value;

        public SequenceNumber(int size) {
            this.size = size;
            this.supportedUTFCharacters = BluetoothTools.getSupportedUTFCharacters();
            this.value = new Character[size];
            for (int i = 0; i < size; i++) {
                this.value[i] = supportedUTFCharacters.get(0);
            }
        }

        /**
         * @param value must contain 3 characters to avoid mistakes
         */
        public SequenceNumber(String value, int size) {
            this.size = size;
            this.supportedUTFCharacters = BluetoothTools.getSupportedUTFCharacters();
            this.value = new Character[size];
            String fixedValue = BluetoothTools.fixLength(value, size, BluetoothTools.FIX_NUMBER);
            for (int i = 0; i < size; i++) {
                this.value[i] = fixedValue.charAt(i);
            }
        }

        public void increment() {
            int count = size;
            while (count > 0) {
                count--;
                if (supportedUTFCharacters.indexOf(value[count]) < supportedUTFCharacters.size() - 1) {
                    value[count] = supportedUTFCharacters.get(supportedUTFCharacters.indexOf(value[count]) + 1);
                    count = 0;
                }
            }
            // if the value is at max we don't increase it
        }

        /**
         * @return negative number if this < sequenceNumber, 0 if this == sequenceNumber and positive if this > sequenceNumber
         **/
        public int compare(@NonNull SequenceNumber sequenceNumber) {
            int result = 0;
            int count = 0;
            while (count < size && result == 0) {
                result = Integer.compare(supportedUTFCharacters.indexOf(value[count]), supportedUTFCharacters.indexOf(sequenceNumber.value[count]));
                count++;
            }
            return result;
        }

        public boolean isMax() {
            SequenceNumber sequenceNumber = clone();
            sequenceNumber.increment();
            return equals(sequenceNumber);  // if we have not been able to increase it means that value is at maximum and therefore will be equal to this object without increment
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof SequenceNumber) {
                SequenceNumber sequenceNumber = (SequenceNumber) obj;
                return sequenceNumber.compare(this) == 0;
            }
            return false;
        }

        @NonNull
        public SequenceNumber clone() {
            return new SequenceNumber(getValue(), size);
        }

        public String getValue() {
            String ret = "";
            for (int i = 0; i < size; i++) {
                ret = ret.concat(value[i].toString());
            }
            return ret;
        }
    }

    //parcel implementation
    public static final Creator<BluetoothMessage> CREATOR = new Creator<>() {
        @NonNull
        @Contract("_ -> new")
        @Override
        public BluetoothMessage createFromParcel(Parcel in) {
            return new BluetoothMessage(in);
        }

        @NonNull
        @Contract(value = "_ -> new", pure = true)
        @Override
        public BluetoothMessage[] newArray(int size) {
            return new BluetoothMessage[size];
        }
    };

    private BluetoothMessage(@NonNull Parcel in) {
        sender = in.readParcelable(Peer.class.getClassLoader());
        id = new SequenceNumber(in.readString(), ID_LENGTH);
        sequenceNumber = new SequenceNumber(in.readString(), SEQUENCE_NUMBER_LENGTH);
        type = in.readInt();
        in.readByteArray(this.data);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeParcelable(sender, i);
        parcel.writeString(id.getValue());
        parcel.writeString(sequenceNumber.getValue());
        parcel.writeInt(type);
        parcel.writeByteArray(this.data);
    }
}
