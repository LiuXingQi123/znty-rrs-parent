package com.znty.rrs.common.config;

import org.junit.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Base64DataSourcePasswordEnvironmentPostProcessor 单元测试。
 */
public class Base64DataSourcePasswordEnvironmentPostProcessorTest {

    /** 验证有效 Base64 密码会在运行时环境中还原。 */
    @Test
    public void postProcessEnvironment_ValidBase64Password_DecodesPassword() {
        StandardEnvironment environment = createEnvironment("c2VjdXJlLXBhc3N3b3Jk");

        new Base64DataSourcePasswordEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertEquals("secure-password", environment.getProperty("spring.datasource.password"));
    }

    /** 验证未配置数据库密码时不新增覆盖属性。 */
    @Test
    public void postProcessEnvironment_PasswordMissing_DoesNotAddOverride() {
        StandardEnvironment environment = new StandardEnvironment();

        new Base64DataSourcePasswordEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertFalse(environment.getPropertySources().contains("base64DataSourcePassword"));
    }

    /** 验证空密码可正常解码为空字符串。 */
    @Test
    public void postProcessEnvironment_EmptyPassword_KeepsEmptyPassword() {
        StandardEnvironment environment = createEnvironment("");

        new Base64DataSourcePasswordEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertEquals("", environment.getProperty("spring.datasource.password"));
    }

    /** 验证非法 Base64 密码会阻止应用启动且不暴露配置内容。 */
    @Test
    public void postProcessEnvironment_InvalidBase64Password_ThrowsSafeException() {
        String invalidPassword = "invalid-password!";
        StandardEnvironment environment = createEnvironment(invalidPassword);

        try {
            new Base64DataSourcePasswordEnvironmentPostProcessor().postProcessEnvironment(environment, null);
            fail("应抛出 Base64 解码异常");
        } catch (IllegalStateException e) {
            assertEquals("数据库密码 Base64 解码失败", e.getMessage());
            assertFalse(e.getMessage().contains(invalidPassword));
        }
    }

    /** 构建带数据库密码配置的运行时环境。 */
    private StandardEnvironment createEnvironment(String password) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "testDataSource",
                Collections.<String, Object>singletonMap("spring.datasource.password", password)));
        return environment;
    }
}
