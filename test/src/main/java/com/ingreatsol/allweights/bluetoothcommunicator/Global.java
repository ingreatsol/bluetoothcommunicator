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

package com.ingreatsol.allweights.bluetoothcommunicator;

import android.annotation.SuppressLint;
import android.app.Application;

import com.ingreatsol.bluetoothcommunicator.BluetoothCommunicator;

public class Global extends Application {
    private BluetoothCommunicator bluetoothCommunicator;

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();

        bluetoothCommunicator = new BluetoothCommunicator(this);
    }

    public BluetoothCommunicator getBluetoothCommunicator() {
        return bluetoothCommunicator;
    }
}
