package com.testing.rfreader;

public class MyUtils {
    private static final String HEXES = "0123456789ABCDEF";
    private static final String SPACE = " ";
    public static String bytesToHex(byte[] data) {
        if (data != null) {
            StringBuilder hex = new StringBuilder(2 * data.length);
            for (int i = 0; i <= data.length - 1; i++) {
                byte dataAtIndex = data[i];
//                hex.append(HEX_INDICATOR);
                hex.append(HEXES.charAt((dataAtIndex & 0xF0) >> 4))
                        .append(HEXES.charAt((dataAtIndex & 0x0F)));
                hex.append(SPACE);
            }
            return hex.toString();
        } else {
            return null;
        }
    }

}
