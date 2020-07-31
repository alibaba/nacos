package com.alibaba.nacos.core.auth.condition;

/**
 * request path info. method:{@link org.springframework.web.bind.annotation.RequestMapping#method()}
 * path: {@link org.springframework.web.bind.annotation.RequestMapping#value()} or {@link
 * org.springframework.web.bind.annotation.RequestMapping#value()}
 *
 * @author horizonzy
 * @since 1.3.1
 */
public class PathRequestCondition {

    private final PathExpression pathExpression;

    public PathRequestCondition(String pathExpression) {
        this.pathExpression = parseExpressions(pathExpression);
    }

    private PathExpression parseExpressions(String pathExpression) {
        String[] split = pathExpression.split("-->");
        String method = split[0];
        String path = split[1];
        return new PathExpression(method, path);
    }

    @Override
    public String toString() {
        return "PathRequestCondition{"
                + "pathExpression="
                + pathExpression
                + '}';
    }

    static class PathExpression {

        private final String method;

        private final String path;

        PathExpression(String method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override
        public String toString() {
            return "PathExpression{"
                    + "method='" + method
                    + '\''
                    + ", path='" + path + '\''
                    + '}';
        }
    }
}
