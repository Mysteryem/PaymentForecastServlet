package uk.co.mysterymayhem.servletjsptest;

import java.util.Arrays;

/**
 * Created by Mysteryem on 24/04/2017.
 */
public class Tests {
    public static void main(String[] args) throws Exception {
        HashTest();
    }

    public static void HashTest() throws Exception {
        byte[] hash = Hasher.hash("merchantPubKey", "payerPubKey", "debitPermissionId", "dueEpoc", "amount");
        String hashString = Hasher.bytesToNiceString(hash);
        if (!Arrays.equals(Hasher.fromPreComputedString(hashString), hash)) {
            throw new RuntimeException("");
        }
    }
}
