package com.linkride.backend.devtools;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Static Bengaluru geography and name pools for the demo seeder (see {@link DemoSeedService}/
 * {@link DemoScenarioService}). Localities are grouped into {@link Corridor}s that mirror real
 * commute routes (e.g. Whitefield -> MG Road along ITPL/Marathahalli/Indiranagar) specifically so
 * that rides and passenger searches drawn from the *same* corridor genuinely overlap -- that's
 * what gives Trip Discovery's corridor/scoring pipeline something real to differentiate between
 * (perfect / partial / weak matches), rather than random points that happen to collide.
 */
final class SeedGeoData {

    private SeedGeoData() {
    }

    record Locality(String name, String address, double lat, double lng) {
    }

    record Corridor(String label, List<Locality> stops) {
    }

    /** Ordered so index order == geographic order along the commute route. */
    static final List<Corridor> CORRIDORS = List.of(
            new Corridor("Whitefield - MG Road", List.of(
                    new Locality("Whitefield", "Whitefield Main Road, Bengaluru", 12.9698, 77.7500),
                    new Locality("ITPL Main Road", "ITPL Main Road, Whitefield, Bengaluru", 12.9855, 77.7365),
                    new Locality("Marathahalli", "Marathahalli Bridge, Bengaluru", 12.9591, 77.6974),
                    new Locality("Domlur", "Domlur Flyover, Bengaluru", 12.9610, 77.6387),
                    new Locality("Indiranagar", "100 Feet Road, Indiranagar, Bengaluru", 12.9719, 77.6412),
                    new Locality("MG Road", "MG Road Metro Station, Bengaluru", 12.9758, 77.6045))),
            new Corridor("Electronic City - MG Road", List.of(
                    new Locality("Electronic City", "Electronic City Phase 1, Bengaluru", 12.8452, 77.6602),
                    new Locality("Silk Board", "Silk Board Junction, Bengaluru", 12.9172, 77.6228),
                    new Locality("BTM Layout", "BTM Layout 2nd Stage, Bengaluru", 12.9166, 77.6101),
                    new Locality("Koramangala", "Koramangala 5th Block, Bengaluru", 12.9352, 77.6245),
                    new Locality("Domlur", "Domlur Flyover, Bengaluru", 12.9610, 77.6387),
                    new Locality("MG Road", "MG Road Metro Station, Bengaluru", 12.9758, 77.6045))),
            new Corridor("Sarjapur - Koramangala", List.of(
                    new Locality("Sarjapur Road", "Sarjapur Road, Bengaluru", 12.9010, 77.6870),
                    new Locality("Bellandur", "Bellandur Junction, Bengaluru", 12.9260, 77.6762),
                    new Locality("HSR Layout", "HSR Layout Sector 2, Bengaluru", 12.9116, 77.6389),
                    new Locality("Koramangala", "Koramangala 5th Block, Bengaluru", 12.9352, 77.6245))),
            new Corridor("Jayanagar - Silk Board", List.of(
                    new Locality("Banashankari", "Banashankari 2nd Stage, Bengaluru", 12.9250, 77.5468),
                    new Locality("JP Nagar", "JP Nagar 6th Phase, Bengaluru", 12.9077, 77.5850),
                    new Locality("Jayanagar", "Jayanagar 4th Block, Bengaluru", 12.9250, 77.5938),
                    new Locality("BTM Layout", "BTM Layout 2nd Stage, Bengaluru", 12.9166, 77.6101),
                    new Locality("Silk Board", "Silk Board Junction, Bengaluru", 12.9172, 77.6228))),
            new Corridor("Yelahanka - MG Road", List.of(
                    new Locality("Yelahanka", "Yelahanka New Town, Bengaluru", 13.1005, 77.5963),
                    new Locality("Hebbal", "Hebbal Flyover, Bengaluru", 13.0357, 77.5970),
                    new Locality("RT Nagar", "RT Nagar Main Road, Bengaluru", 13.0189, 77.5937),
                    new Locality("Cantonment", "Bengaluru Cantonment Station, Bengaluru", 12.9850, 77.6046),
                    new Locality("MG Road", "MG Road Metro Station, Bengaluru", 12.9758, 77.6045))),
            new Corridor("Yeshwanthpur - Malleshwaram", List.of(
                    new Locality("Yeshwanthpur", "Yeshwanthpur Circle, Bengaluru", 13.0284, 77.5540),
                    new Locality("Rajajinagar", "Rajajinagar 1st Block, Bengaluru", 12.9915, 77.5527),
                    new Locality("Malleshwaram", "Malleshwaram Circle, Bengaluru", 13.0067, 77.5751),
                    new Locality("Cantonment", "Bengaluru Cantonment Station, Bengaluru", 12.9850, 77.6046))));

    private static final String[] FIRST_NAMES = {
            "Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh", "Krishna", "Ishaan", "Rohan",
            "Ananya", "Diya", "Aadhya", "Saanvi", "Myra", "Anika", "Pari", "Ira", "Kavya", "Riya",
            "Rahul", "Karthik", "Suresh", "Naveen", "Manoj", "Deepak", "Kiran", "Ravi", "Vikram", "Arun",
            "Priya", "Sneha", "Divya", "Pooja", "Neha", "Shruti", "Meera", "Lakshmi", "Swathi", "Nandini"
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Verma", "Gupta", "Reddy", "Rao", "Kumar", "Nair", "Iyer", "Menon", "Pillai",
            "Gowda", "Shetty", "Hegde", "Kulkarni", "Bhat", "Naidu", "Chowdhury", "Patel", "Desai", "Joshi"
    };

    static String randomFullName(ThreadLocalRandom random) {
        String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        return first + " " + last;
    }

    static String randomPhoneNumber(ThreadLocalRandom random) {
        // Indian mobile numbers start 6-9; keep it a fixed 10 digits.
        StringBuilder sb = new StringBuilder();
        sb.append((char) ('6' + random.nextInt(4)));
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    static Corridor randomCorridor(ThreadLocalRandom random) {
        return CORRIDORS.get(random.nextInt(CORRIDORS.size()));
    }

    static final String[] CAR_MAKES_MODELS = {
            "Maruti Suzuki|Swift", "Maruti Suzuki|Baleno", "Maruti Suzuki|Dzire", "Hyundai|i20",
            "Hyundai|Creta", "Honda|City", "Honda|Amaze", "Tata|Nexon", "Tata|Altroz", "Toyota|Innova",
            "Kia|Seltos", "Mahindra|XUV300", "Volkswagen|Polo", "Renault|Kwid"
    };

    static final String[] COLOURS = {"White", "Silver", "Grey", "Red", "Blue", "Black"};
}
