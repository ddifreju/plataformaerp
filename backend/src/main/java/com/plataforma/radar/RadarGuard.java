package com.plataforma.radar;

import com.plataforma.autenticacao.UsuarioAutenticado;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Configuration
public class RadarGuard {
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> radarPermissions(JdbcTemplate db) {
        var bean = new FilterRegistrationBean<OncePerRequestFilter>();
        bean.setOrder(-98);
        bean.setFilter(
                new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(
                            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws ServletException, IOException {
                        var auth = SecurityContextHolder.getContext().getAuthentication();
                        String path = req.getRequestURI();
                        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
                            String role =
                                    db.queryForObject(
                                            "select papel from usuario where tenant_id=? and id=?"
                                                    + " and ativo",
                                            String.class,
                                            u.tenantId(),
                                            u.usuarioId());
                            boolean finance = Set.of("DONO", "GESTOR", "FINANCEIRO").contains(role);
                            boolean denied =
                                    (path.startsWith("/api/margem") || path.equals("/api/pergunta"))
                                            && !finance;
                            denied |=
                                    path.equals("/api/painel/gestor")
                                            && !Set.of("DONO", "GESTOR").contains(role);
                            denied |=
                                    path.startsWith("/api/canais/")
                                            && !req.getMethod().equals("GET")
                                            && !Set.of("DONO", "GESTOR").contains(role);
                            // Non-simple header cannot be supplied by a cross-origin HTML form. No
                            // cross-origin allowance for this header.
                            denied |=
                                    path.startsWith("/api/radar")
                                            && !Set.of("GET", "HEAD", "OPTIONS")
                                                    .contains(req.getMethod())
                                            && !"1".equals(req.getHeader("X-Radar-Request"));
                            if (denied) {
                                res.setStatus(403);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter()
                                        .write(
                                                "{\"mensagem\":\"Operação não permitida para esta"
                                                        + " sessão.\"}");
                                return;
                            }
                        }
                        chain.doFilter(req, res);
                    }
                });
        bean.addUrlPatterns("/api/*");
        return bean;
    }
}
