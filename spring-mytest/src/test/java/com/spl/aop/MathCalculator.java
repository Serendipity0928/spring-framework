package com.spl.aop;

import org.springframework.stereotype.Component;

/**
 * 计算测试类
 */
@Component
public class MathCalculator {
	public int div(int i, int j) {
		return i/j;
	}
}
