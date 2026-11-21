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

/** Translates the one expected database conflict outside the transaction interceptor. */
@Configuration(proxyBeanMethods = false)
class CreateHouseholdConflictAdvisor {
  private static final String CONSTRAINT = "uq_household_memberships_active_account";

  @Bean
  static Advisor createHouseholdConflictTranslation() {
    var pointcut =
        new StaticMethodMatcherPointcut() {
          @Override
          public boolean matches(Method method, Class<?> targetClass) {
            return CreateHouseholdService.class.isAssignableFrom(targetClass)
                && method.getName().equals("create")
                && method.getParameterCount() == 1;
          }
        };
    MethodInterceptor interceptor =
        invocation -> {
          try {
            return invocation.proceed();
          } catch (RuntimeException error) {
            if (isActiveMembershipConflict(error)) {
              throw new ActiveHouseholdExistsException();
            }
            throw error;
          }
        };
    var advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
    advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return advisor;
  }

  private static boolean isActiveMembershipConflict(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
        return matchesConstraint(sql);
      }
    }
    return false;
  }

  private static boolean matchesConstraint(SQLException error) {
    try {
      Object detail = error.getClass().getMethod("getServerErrorMessage").invoke(error);
      return detail != null
          && CONSTRAINT.equals(detail.getClass().getMethod("getConstraint").invoke(detail));
    } catch (ReflectiveOperationException | SecurityException unavailable) {
      return false;
    }
  }
}
