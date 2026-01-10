import api.SimpleDb;

public class Main {

    public static void main(String[] args) throws Exception {
        SimpleDb db = new SimpleDb("data.db");

        db.put("user:1", "Aakash1");
        db.put("user:2", "Renisha1");
        db.put("city", "SanJuanFran1");

        System.out.println("PUT operations completed.");

        System.out.println(db.get("user:1")); // Aakash
        System.out.println(db.get("user:2")); // Renisha
//        db.close();

        db.put("user:3", "Aakash1");
        db.put("user:4", "Renisha1");
        db.put("city2", "MelHurne1");

        System.out.println(db.get("user:4"));



    }
}
