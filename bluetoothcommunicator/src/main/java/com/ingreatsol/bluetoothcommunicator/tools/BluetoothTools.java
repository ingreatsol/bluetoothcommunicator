package com.ingreatsol.bluetoothcommunicator.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;

/**
 * This class contains a series of static methods to help the library, for the user there is an important method however:
 * getSupportedUTFCharacters returns the list of supported characters that the user can compare with the characters of the name
 * that will be passed to BluetoothCommunicator in the constructor o in setName, in fact the name must not contain
 * characters other than those supported and must not exceed 18 characters in length, otherwise BluetoothCommunicator
 * may not work correctly.
 */
public class BluetoothTools {
    public static final int FIX_NUMBER = 0;
    public static final int FIX_TEXT = 1;

    /**
     * return all characters of UTF encoding (this is because bluetooth only support a certain amount of bytes
     * to send to nearby devices) so ensure that the name that will be passed to BluetoothCommunicator in the
     * constructor o in setName contains only these character and not exceed 18 characters in length, otherwise
     * BluetoothCommunicator may not work correctly.
     *
     * @return list of supported characters
     */
    @NonNull
    public static ArrayList<Character> getSupportedUTFCharacters() {
        Character[] names = {' ', '!', '"', '#', '$', '%', '&', '\'', '(', ')',
                '*', '+', ',', '-', '.', '/',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                ':', ';', '<', '=', '>', '?', '@',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                '[', '\\', ']', '^', '_', '`',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                '{', '|', '}', '~'};

        ArrayList<Character> characters = new ArrayList<>(Arrays.asList(names));

        Collections.sort(characters);  // alphabetical order

        return characters;
    }

    @NonNull
    public static String getSupportedNameCharactersString() {
        String string = "";
        ArrayList<Character> characters = getSupportedUTFCharacters();
        for (Character character : characters) {
            string = string.concat(" " + character.toString());
        }
        int lentgh = string.getBytes(StandardCharsets.UTF_8).length;
        Log.e("lenght", lentgh + "");
        return string;
    }

    public static String fixLength(@NonNull String string, int length, int typeOfFix) {
        int fillingLength = length - string.length();
        if (fillingLength > 0) {
            // filling
            Character fillChar = getSupportedUTFCharacters().get(0);
            StringBuilder outputBuffer = new StringBuilder(fillingLength);
            for (int i = 0; i < fillingLength; i++) {
                outputBuffer.append(fillChar);
            }
            if (typeOfFix == FIX_NUMBER) {
                return outputBuffer.toString().concat(string);
            } else {
                return string.concat(outputBuffer.toString());
            }
        } else {
            // cut
            if (typeOfFix == FIX_NUMBER) {
                return string.substring(fillingLength * -1);
            } else {
                return string.substring(0, length);
            }
        }
    }

    @NonNull
    public static String generateRandomUTFString(int length) {
        return new RandomString(length).nextString();
    }

    @NonNull
    public static String generateBluetoothNameId(Context context) {
        SharedPreferences sharedPreferences;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            String sharedPreferencename = PreferenceManager.getDefaultSharedPreferencesName(context);
            sharedPreferences = context.getSharedPreferences(sharedPreferencename, Context.MODE_PRIVATE);
        }
        else {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        }

        String id = sharedPreferences.getString("bluetoothNameId", "");
        if (Objects.isNull(id) || id.length() != 2) {
            //generazione dell'id e salvataggio
            SharedPreferences.Editor edit = sharedPreferences.edit();
            id = generateRandomUTFString(2);
            edit.putString("bluetoothNameId", id);
            edit.apply();
        }
        return id;
    }

    /**
     * index cell goes in second array
     */
    @NonNull
    public static ArrayDeque<byte[]> splitBytes(@NonNull byte[] array, int subArraysLength) {

        ArrayDeque<byte[]> resultMatrixList = new ArrayDeque<>();
        for (int j = 0; j < array.length; j += subArraysLength) {
            byte[] subArray;
            if (j + subArraysLength < array.length) {
                subArray = new byte[subArraysLength];
            } else {
                subArray = new byte[array.length - j];
            }
            System.arraycopy(array, j, subArray, 0, subArray.length);
            resultMatrixList.addLast(subArray);
        }

        return resultMatrixList;
    }

    @NonNull
    public static byte[] concatBytes(@NonNull byte[]... arrays) {
        int resultArrayLength = 0;
        for (byte[] array : arrays) {
            resultArrayLength += array.length;
        }
        byte[] resultArray = new byte[resultArrayLength];
        int destIndex = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, resultArray, destIndex, array.length);
            destIndex += array.length;
        }
        return resultArray;
    }

    @Nullable
    public static byte[] subBytes(byte[] array, int begin, int end) {
        if (end <= begin || begin < 0 || end > array.length) {
            return null;
        }
        int length = end - begin;
        byte[] subArray = new byte[length];
        System.arraycopy(array, begin, subArray, 0, length);
        return subArray;
    }

    private static class RandomString {
        private final Random random;
        private final char[] buf;

        /**
         * Create an alphanumeric string generator.
         */
        private RandomString(int length, Random random) {
            if (length < 1) throw new IllegalArgumentException();
            this.random = Objects.requireNonNull(random);
            this.buf = new char[length];
        }

        /**
         * Create an alphanumeric strings from a secure generator.
         */
        private RandomString(int length) {
            this(length, new SecureRandom());
        }

        /**
         * Generate a random string.
         */
        @NonNull
        @Contract(" -> new")
        private String nextString() {
            for (int idx = 0; idx < buf.length; ++idx) {
                buf[idx] = (getSupportedUTFCharacters().get(random.nextInt(95)));  //si genera un carattere casuale composto da tutti i valori possibili del codice ascii normale (non esteso) per poter essere espressi da un solo byte in utf-8
            }
            return new String(buf);
        }
    }
}
