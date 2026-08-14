package v.akfz.db.generator;

public class NotAClass implements InitializerClass {

    @Override
    public void init() {
        throw new RuntimeException("HowWhatAndWhy");
    }
}
