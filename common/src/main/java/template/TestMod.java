package template;

import v.akfz.db.generator.GenerateInitializer;
import v.akfz.db.generator.InitializerClass;
import v.akfz.db.generator.LoaderType;

@GenerateInitializer(
        loader = LoaderType.Both,
        modId = "testmod"
)
public class TestMod implements InitializerClass {

    @Override
    public void init() {
        System.out.println("Инициализация мода прошла успешно!");
    }
}
