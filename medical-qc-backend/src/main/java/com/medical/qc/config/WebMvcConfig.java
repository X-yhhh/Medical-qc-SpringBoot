package com.medical.qc.config;

import com.medical.qc.modules.auth.persistence.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * 数据链路：静态 uploads 资源映射 + API 登录拦截器注册。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 /uploads/** 映射到本地 uploads 目录，供前端直接预览影像和截图。
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 所有业务 API 默认都要求登录，登录/注册和静态资源路径例外。
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/current", // Allow checking status
                        "/uploads/**" // Allow static resources
                );
    }

    /**
     * 简单登录拦截器。
     * 当前仅基于 Session 中是否存在 user 快照判断是否已登录。
     */
    class LoginInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            // 允许 CORS 预检请求直接通过，避免前端跨域协商失败。
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            // 只要 Session 中存在 user 对象，就视为已登录，后续细粒度权限由业务层判断。
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("user") != null) {
                return true;
            }

            // 未登录时返回统一 401 JSON 结构，供请求拦截器识别。
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\": \"Not authenticated\"}");
            return false;
        }
    }
}

