package mariposa.e2e;

import com.intuit.karate.junit5.Karate;

class PlatformE2ETest {

    @Karate.Test
    Karate platform() {
        return Karate.run("classpath:mariposa/e2e").outputCucumberJson(true);
    }
}
