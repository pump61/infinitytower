package com.eternity.infinitytower.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class PartyKeyUtil {

    private PartyKeyUtil() {}

    public static String partyKeyFromMembers(Collection<UUID> members) {
        List<String> sorted = new ArrayList<>();
        for (UUID u : members) sorted.add(u.toString());
        Collections.sort(sorted);

        String joined = String.join("|", sorted);
        return sha1Hex(joined);
    }

    public static String membersCsv(Collection<UUID> members) {
        List<String> sorted = new ArrayList<>();
        for (UUID u : members) sorted.add(u.toString());
        Collections.sort(sorted);
        return String.join(",", sorted);
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] out = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
