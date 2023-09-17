package com.spl.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MemberSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 测试切面类
 */
@Aspect
@Component
public class LogAspects {

	// 声明切点为 MathCalculator下的所有方法
	@Pointcut("execution(public int com.spl.aop.MathCalculator.*(..))")
	public void pointCut() {}

	//环绕通知
	@Around("pointCut()")
	public Object doAround(ProceedingJoinPoint pj) {
		// 获取被增强的目标对象，然后获取目标对象的Class
		Class<?> targetClass = pj.getTarget().getClass();
		System.out.println("执行Around,被增强的目标类为：" + targetClass);

		// 方法名称
		String methodName = pj.getSignature().getName();
		System.out.println("执行Around,目标方法名称为：" + methodName);

		// 目标方法的参数类型
		Class[] parameterTypes = ((MethodSignature) pj.getSignature()).getParameterTypes();

		// 目标方法的入参
		Object[] args = pj.getArgs();
		System.out.println("执行Around,方法入参为：" + Arrays.toString(args));
		try {
			// 目标方法
			Method method = targetClass.getMethod(methodName, parameterTypes);
			System.out.println("执行Around，方法为：" + method);
			// 继续放行
			return pj.proceed();
		} catch (Throwable e) {
			System.err.println("执行Around异常..." + e);
			return "error";
		}
	}

	/** 前置通知 */
	@Before("pointCut()")
	public void logStart(JoinPoint joinPoint) {
		Object[] args = joinPoint.getArgs();
		System.out.println(joinPoint.getSignature().getName() + "运行Before... 参数为：" + Arrays.asList(args));
	}
	/** 后置通知 */
	@After("pointCut()")
	public void logEnd(JoinPoint joinPoint) {
		System.out.println(joinPoint.getSignature().getName() + "运行After...");
	}
	/** 返回通知 */
	@AfterReturning(value = "pointCut()", returning = "result")
	public void logReturn(JoinPoint joinPoint, Object result) {
		System.out.println(joinPoint.getSignature().getName() + "运行AfterReturning... 正常返回，结果为：" + result);
	}
	/** 异常通知 */
	@AfterThrowing(value = "pointCut()", throwing = "exception")
	public void logException(JoinPoint joinPoint, Exception exception) {
		System.out.println(joinPoint.getSignature().getName() + "运行AfterThrowing... 异常信息：" + exception);
	}
}
