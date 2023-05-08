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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ingreatsol.allweights.bluetoothcommunicator.fragments.ConversationFragment;
import com.ingreatsol.allweights.bluetoothcommunicator.fragments.PairingFragment;
import com.ingreatsol.allweights.bluetoothcommunicator.tools.Tools;
import com.ingreatsol.bluetoothcommunicator.BluetoothCommunicator;
import com.ingreatsol.bluetoothcommunicator.Peer;
import com.ingreatsol.bluetoothcommunicator.test.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final int PAIRING_FRAGMENT = 0;
    public static final int CONVERSATION_FRAGMENT = 1;
    public static final int DEFAULT_FRAGMENT = PAIRING_FRAGMENT;
    public static final int NO_PERMISSIONS = -10;
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 2;
    private Global global;
    private int currentFragment = -1;
    private final ArrayList<Callback> clientsCallbacks = new ArrayList<>();
    private FrameLayout fragmentContainer;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String[]> multiplePermissionLauncher;
    private ActivityResultLauncher<Intent> openAppSettingsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        global = (Global) getApplication();

        initLauchers();

        // Clean fragments (only if the app is recreated (When user disable permission))
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        // Remove previous fragments (case of the app was restarted after changed permission on android 6 and higher)
        List<Fragment> fragmentList = fragmentManager.getFragments();
        for (Fragment fragment : fragmentList) {
            if (fragment != null) {
                fragmentManager.beginTransaction().remove(fragment).commit();
            }
        }

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        fragmentContainer = findViewById(R.id.fragment_container);

        global.getBluetoothCommunicator().addCallback(new BluetoothCommunicator.Callback() {
            @Override
            public void onAdvertiseStarted() {
                super.onAdvertiseStarted();
                if (global.getBluetoothCommunicator().isDiscovering()) {
                    notifySearchStarted();
                }
            }

            @Override
            public void onDiscoveryStarted() {
                super.onDiscoveryStarted();
                if (global.getBluetoothCommunicator().isAdvertising()) {
                    notifySearchStarted();
                }
            }

            @Override
            public void onAdvertiseStopped() {
                super.onAdvertiseStopped();
                if (!global.getBluetoothCommunicator().isDiscovering()) {
                    notifySearchStopped();
                }
            }

            @Override
            public void onDiscoveryStopped() {
                super.onDiscoveryStopped();
                if (!global.getBluetoothCommunicator().isAdvertising()) {
                    notifySearchStopped();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // when we return to the app's gui we choose which fragment to start based on connection status
        if (global.getBluetoothCommunicator().getConnectedPeersList().size() == 0) {
            setFragment(DEFAULT_FRAGMENT);
        } else {
            setFragment(CONVERSATION_FRAGMENT);
        }
    }

    private void selectTipeLauncherPermission(@NonNull String... permissions) {
        if (permissions.length == 1) {
            requestPermissionLauncher.launch(permissions[0]);
        } else if (permissions.length > 1) {
            multiplePermissionLauncher.launch(permissions);
        }
    }

    private void manejarDenegacionDePermiso() {
        ArrayList<String> permissionsShould = Tools
                .shouldMapPermission(this, Tools.getPermissionEscanearBluetooth().toArray(new String[0]));
        if (permissionsShould.size() > 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Estas seguro?")
                    .setMessage("Allweights no puede funcionar correctamente si deniegas este permiso")
                    .setNegativeButton("Si, denegar", (dialogCancel, which) -> dialogCancel.dismiss())
                    .setPositiveButton("Intentar de nuevo", (dialogAcept, which) -> {
                        selectTipeLauncherPermission(Tools.getPermissionEscanearBluetooth().toArray(new String[0]));
                        dialogAcept.dismiss();
                    })
                    .show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Configuración de la aplicación?")
                    .setMessage("El permiso solicitado ha sido denegado, para activar este permiso debe ir a la configuración de la aplicación")
                    .setNegativeButton("Entrar sin permiso", (dialogCancel, which) -> dialogCancel.dismiss())
                    .setPositiveButton("Ir a configuración", (dialogAcept, which) -> openAppSettingsLauncher.launch(ajustesAplicacion(this)))
                    .show();
        }
    }

    @NonNull
    public Intent ajustesAplicacion(@NonNull FragmentActivity fragmentActivity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + fragmentActivity.getPackageName()));
        return intent;
    }

    @SuppressLint("MissingPermission")
    public void initLauchers() {
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startSearch();
                    } else {
                        manejarDenegacionDePermiso();
                        Toast.makeText(this, "Permisos denegados", Toast.LENGTH_LONG).show();
                    }
                });

        multiplePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean resultStatus = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                resultStatus = result.entrySet().stream().allMatch(Map.Entry::getValue);
            } else {
                for (boolean entry : result.values()) {
                    if (!entry) {
                        resultStatus = false;
                        break;
                    } else {
                        resultStatus = true;
                    }
                }
            }
            if (resultStatus) {
                startSearch();
            } else {
                manejarDenegacionDePermiso();
                Toast.makeText(this, "Permisos denegados", Toast.LENGTH_LONG).show();
            }
        });

        openAppSettingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> startSearch());

    }

    public void setFragment(int fragmentName) {
        switch (fragmentName) {
            case PAIRING_FRAGMENT: {
                // possible setting of the fragment
                if (getCurrentFragment() != PAIRING_FRAGMENT) {
                    PairingFragment paringFragment = new PairingFragment();
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                    transaction.replace(R.id.fragment_container, paringFragment);
                    transaction.commit();
                    currentFragment = PAIRING_FRAGMENT;
                }
                break;
            }
            case CONVERSATION_FRAGMENT: {
                // possible setting of the fragment
                if (getCurrentFragment() != CONVERSATION_FRAGMENT) {
                    ConversationFragment conversationFragment = new ConversationFragment();
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                    transaction.replace(R.id.fragment_container, conversationFragment);
                    transaction.commit();
                    currentFragment = CONVERSATION_FRAGMENT;
                }
                break;
            }
        }
    }

    public int getCurrentFragment() {
        if (currentFragment != -1) {
            return currentFragment;
        } else {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment != null) {
                if (currentFragment.getClass().equals(PairingFragment.class)) {
                    return PAIRING_FRAGMENT;
                }
                if (currentFragment.getClass().equals(ConversationFragment.class)) {
                    return CONVERSATION_FRAGMENT;
                }
            }
        }
        return -1;
    }

    @Override
    public void onBackPressed() {
        DialogInterface.OnClickListener confirmExitListener = (dialog, which) -> exitFromConversation();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment != null) {
            if (fragment instanceof ConversationFragment) {
                showConfirmExitDialog(confirmExitListener);
            } else {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    public void exitFromConversation() {
        if (global.getBluetoothCommunicator().getConnectedPeersList().size() > 0) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (fragment instanceof ConversationFragment) {
                ConversationFragment conversationFragment = (ConversationFragment) fragment;
                conversationFragment.appearLoading();
            }
            global.getBluetoothCommunicator().disconnectFromAll();
        } else {
            setFragment(DEFAULT_FRAGMENT);
        }
    }

    protected void showConfirmExitDialog(DialogInterface.OnClickListener confirmListener) {
        //creazione del dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage("Confirm exit");
        builder.setPositiveButton(android.R.string.ok, confirmListener);
        builder.setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public int startSearch() {
        if (global.getBluetoothCommunicator().isBluetoothLeSupported() != BluetoothCommunicator.SUCCESS) {
            return BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED;
        }

        if (!Tools.hasPermissions(this, Tools.getPermissionEscanearBluetooth().toArray(new String[0]))) {
            requestPermissions(Tools.getPermissionEscanearBluetooth().toArray(new String[0]), REQUEST_CODE_REQUIRED_PERMISSIONS);
            return NO_PERMISSIONS;
        }

        @SuppressLint("MissingPermission") int advertisingCode = global.getBluetoothCommunicator().startAdvertising();
        @SuppressLint("MissingPermission") int discoveringCode = global.getBluetoothCommunicator().startDiscovery();
        if (advertisingCode == discoveringCode) {
            return advertisingCode;
        }
        if (advertisingCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED || discoveringCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED) {
            return BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED;
        }
        if (advertisingCode == BluetoothCommunicator.SUCCESS || discoveringCode == BluetoothCommunicator.SUCCESS) {
            if (advertisingCode == BluetoothCommunicator.ALREADY_STARTED || discoveringCode == BluetoothCommunicator.ALREADY_STARTED) {
                return BluetoothCommunicator.SUCCESS;
            }
        }
        return BluetoothCommunicator.ERROR;
    }

    public int stopSearch(boolean tryRestoreBluetoothStatus) {
        @SuppressLint("MissingPermission") int advertisingCode = global.getBluetoothCommunicator().stopAdvertising();
        @SuppressLint("MissingPermission") int discoveringCode = global.getBluetoothCommunicator().stopDiscovery();
        if (advertisingCode == discoveringCode) {
            return advertisingCode;
        }
        if (advertisingCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED || discoveringCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED) {
            return BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED;
        }
        if (advertisingCode == BluetoothCommunicator.SUCCESS || discoveringCode == BluetoothCommunicator.SUCCESS) {
            if (advertisingCode == BluetoothCommunicator.ALREADY_STOPPED || discoveringCode == BluetoothCommunicator.ALREADY_STOPPED) {
                return BluetoothCommunicator.SUCCESS;
            }
        }
        return BluetoothCommunicator.ERROR;
    }

    public boolean isSearching() {
        return global.getBluetoothCommunicator().isAdvertising() && global.getBluetoothCommunicator().isDiscovering();
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void connect(Peer peer) {
        stopSearch(false);
        global.getBluetoothCommunicator().connect(peer);
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void acceptConnection(Peer peer) {
        global.getBluetoothCommunicator().acceptConnection(peer);
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public void rejectConnection(Peer peer) {
        global.getBluetoothCommunicator().rejectConnection(peer);
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    public int disconnect(Peer peer) {
        return global.getBluetoothCommunicator().disconnect(peer);
    }

    public FrameLayout getFragmentContainer() {
        return fragmentContainer;
    }


    public void addCallback(Callback callback) {
        // in this way the listener will listen to both this activity and the communicatorexample
        global.getBluetoothCommunicator().addCallback(callback);
        clientsCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        global.getBluetoothCommunicator().removeCallback(callback);
        clientsCallbacks.remove(callback);
    }

    private void notifyMissingSearchPermission() {
        for (int i = 0; i < clientsCallbacks.size(); i++) {
            clientsCallbacks.get(i).onMissingSearchPermission();
        }
    }

    private void notifySearchPermissionGranted() {
        for (int i = 0; i < clientsCallbacks.size(); i++) {
            clientsCallbacks.get(i).onSearchPermissionGranted();
        }
    }

    private void notifySearchStarted() {
        for (int i = 0; i < clientsCallbacks.size(); i++) {
            clientsCallbacks.get(i).onSearchStarted();
        }
    }

    private void notifySearchStopped() {
        for (int i = 0; i < clientsCallbacks.size(); i++) {
            clientsCallbacks.get(i).onSearchStopped();
        }
    }

    public static class Callback extends BluetoothCommunicator.Callback {
        public void onSearchStarted() {
        }

        public void onSearchStopped() {
        }

        public void onMissingSearchPermission() {
        }

        public void onSearchPermissionGranted() {
        }
    }
}