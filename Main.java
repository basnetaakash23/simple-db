import api.SimpleDb;

public class Main {

    public static void main(String[] args) throws Exception {
        SimpleDb db = new SimpleDb("data.db");

        db.post("user:1", "Aakash1");
        db.post("user:2", "Renisha1");
        db.post("city", "SanJuanFran1");

        System.out.println("PUT operations completed.");

        System.out.println(db.get("user:1")); // Aakash
        System.out.println(db.get("user:2")); // Renisha
//        db.close();

        db.update("user:2", "Renisha2");

//        System.out.println(db.get("user:2"));

//        db.delete("city2");
//        db.delete("city");
//        System.out.println(db.get("city2"));
//        System.out.println(db.get("city"));

//        System.out.println(db.patch("user:1","Pikashasha"));
        System.out.println(db.get("user:1"));





    }
}
