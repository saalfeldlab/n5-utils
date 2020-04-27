package org.janelia.saalfeldlab.cosem;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class CosemClassToId {

    private final Map<String, Integer> mapping;

    public CosemClassToId() throws IOException {

        mapping = new HashMap<>();
        try (final InputStream stream = getClass().getClassLoader().getResourceAsStream("cosem-classes.txt")) {
            try (final Scanner scanner = new Scanner(stream)) {
                while (scanner.hasNext()) {
                    final int id = scanner.nextInt();
                    final String line = scanner.nextLine().trim();
                    mapping.put(getClassKey(line), id);
                }
            }
        }
    }

    int getClassId(final String name) {

        final String key = getClassKey(name);
        return mapping.containsKey(key) ? mapping.get(key) : -1;
    }

    String getClassKey(final String name) {

        return name.trim().replace(' ', '_').toLowerCase();
    }
}
