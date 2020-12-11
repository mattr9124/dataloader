/*
 * Copyright (c) 2015, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.dataloader.security;

import java.security.GeneralSecurityException;

public class EncryptionUtil {

    /**
     * Convert text to bytes
     *
     * @return bytes for the input text
     */
    public static byte[] textToBytes(String text) {
        byte[] baBytes = new byte[text.length() / 2];
        for (int j = 0; j < text.length() / 2; j++) {
            Integer tmpInteger = Integer.decode(new String("0x" + text.substring(j * 2, (j * 2) + 2)));
            int tmpValue = tmpInteger.intValue();
            if (tmpValue > 127) // fix negatives
            {
                tmpValue = (tmpValue - 127) * -1;
            }
            tmpInteger = Integer.valueOf(tmpValue);
            baBytes[j] = tmpInteger.byteValue();
        }

        return baBytes;
    }

    /**
     * Convert bytes to text
     *
     * @return text for the input bytes
     */
    public static String bytesToText(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            int num = bytes[i];
            if (num < 0) num = 127 + (num * -1); // fix negative back to positive
            String hex = Integer.toHexString(num);
            if (hex.length() == 1) {
                hex = "0" + hex; // ensure 2 digits
            }
            sb.append(hex);
        }
        return sb.toString();
    }


    private static void printUsage() {
        String usage = "\nUtility to encrypt a string based on a static or a provided key\n"
                        + "Options (mutually exclusive - use one at a time): \n"
                        + "\t-e <plain text> <Path to keyfile>                Encrypt a plain text value, keyfile path\n"
                        + "\t-d <encryptText> <Path to keyfile>               Decrypt an encrypted text back to plain text value using keyfile\n"
                        + "\t-k [Path to Keyfile]                             Generate keyfile with optional keyfile path\n";

        System.out.println(usage);
    }

    public static void main(String[] args) {
        // args[0] = input data, required
        // args[1] = key (optional)
        if (args.length < 1) {
            printUsage();
            System.exit(-1);
        }

        int i = 0;
        String option = args[i];
        if (option.length() < 2 || option.charAt(0) != '-') {
            System.out.println("Invalid option format: " + args[i]);
            System.exit(-1);
        }
        // make sure enough arguments are provided
        if (arrayTooSmall(args, i) && option.charAt(1) != 'k') {
            System.out.println("Option '" + option + "' requires at least one parameter.  Please check usage.\n");
            printUsage();
            System.exit(-1);
        }
        // advance index to param and save the param value
        String param = null;
        switch (option.charAt(1)) {
            case 'e':
                EncryptionAesUtil enc = new EncryptionAesUtil();
                param = args[++i];
                if (!arrayTooSmall(args, i)) {
                    String keyFilename = args[++i];
                    try {
                        enc.setCipherKeyFromFilePath(keyFilename);
                    } catch (Exception e) {
                        System.out.println("Error setting the key from file: "
                                + keyFilename + ", error: " + e.getMessage());
                        System.exit(-1);
                    }
                }
                try {
                    String encrypted = enc.encryptMsg(param);
                    System.out.println("The output string of encryption is: \n" + encrypted);
                } catch (Exception e) {
                    System.out.println("Error setting the key: " + e.getMessage());
                    System.exit(-1);
                }

                break;

            case 'k':
                // optional [Path to key file]
                try {
                    EncryptionAesUtil encAes = new EncryptionAesUtil();
                    if (i == args.length - 2 || i == args.length - 1) {
                        String filePath = encAes.createKeyFileIfNotExisting(i == args.length - 1 ? null : args[i + 1]);
                        System.out.println("Keyfile \"" + filePath + "\" was created! ");
                    } else {
                        System.out.println("Please provide correct parameters!");
                        printUsage();
                        System.exit(-1);
                    }
                } catch (Exception e) {
                    System.out.println("Error occurred:  " + e.getMessage());
                }
                break;

            case 'd':
                EncryptionAesUtil encAes = new EncryptionAesUtil();
                String encryptMsg = args[++i];
                if (!arrayTooSmall(args, i)) {
                    String keyFilename = args[++i];
                    try {
                        encAes.setCipherKeyFromFilePath(keyFilename);
                    } catch (GeneralSecurityException e) {
                        System.out.println("Failed in decryption: " + e.getMessage() + "\n Make sure using the same keyfile to decrypt.");
                        System.exit(-1);
                    }
                }
                try {
                    String plainText = encAes.decryptMsg(encryptMsg);
                    System.out.println("The output string of decryption is: \n" + plainText);
                } catch (Exception e) {
                    System.out.println("Failed in decryption: " + e.getMessage() + "\n Make sure using the same keyfile to decrypt.");
                    System.exit(-1);
                }
                break;

            default:
                System.out.println("Unsupported option: " + option);
                printUsage();
                System.exit(-1);
        }
    }

    /**
     * @return true if array is too small to increment the index
     */
    private static boolean arrayTooSmall(String[] array, int index) {
        return (index + 1) > (array.length - 1);
    }
}
