package v.akfz.db.generator;

/***
 * read {@link GenerateInitializer}.
 * <p>
 * Warning, its class is not exist in compiled jar, so good practice its
 * <pre>{@code
 * @GenerateInitializer(modId = "aslib")
 * public class AsLib {
 *
 *     public void init() {
 *         // Your initialization logic here
 *     }
 * }
 * }</pre>
 */
@Deprecated(forRemoval = true)
public interface InitializerClass {
    void init();
}
