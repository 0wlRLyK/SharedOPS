package dev.sharedops.household.application;

import java.lang.reflect.Method;
import java.sql.SQLException;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Translate the named account uniqueness conflict after the transaction has rolled back. */
@Configuration(proxyBeanMethods = false)
class InvitationConflictAdvisor {
  private static final String CONSTRAINT = "uq_household_memberships_active_account";

  @Bean
  static Advisor invitationConflictTranslation() {
    var pointcut =
        new StaticMethodMatcherPointcut() {
          @Override
          public boolean matches(Method method, Class<?> targetClass) {
            return InvitationService.class.isAssignableFrom(targetClass)
                && method.getName().equals("accept")
                && method.getParameterCount() == 1;
          }
        };
    MethodInterceptor interceptor =
        invocation -> {
          try {
            return invocation.proceed();
          } catch (RuntimeException error) {
            if (isAccountConflict(error)) {
              throw new InvitationConflictException();
            }
            throw error;
          }
        };
    var advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
    advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return advisor;
  }

  private static boolean isAccountConflict(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
        try {
          Object detail = sql.getClass().getMethod("getServerErrorMessage").invoke(sql);
          return detail != null
              && CONSTRAINT.equals(detail.getClass().getMethod("getConstraint").invoke(detail));
        } catch (ReflectiveOperationException | SecurityException unavailable) {
          return false;
        }
      }
    }
    return false;
  }
}
