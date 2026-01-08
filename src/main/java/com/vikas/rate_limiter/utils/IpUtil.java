package com.vikas.rate_limiter.utils;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtil {

	public static String getUserIp(
			HttpServletRequest req) { // WARN: Why use static here? Is it so that we don't need to instantite
		// IpUtil? What are its disadvantages and advanatges? WHynot make all
		// methods static? And what are other options if I don't wanna use
		// static?
		String[] headers = { "X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP" };

		for (String header : headers) {
			String val = req.getHeader(header);

			if (val != null && !val.isEmpty() && !"unknown".equalsIgnoreCase(val)) {
				return val.split(",")[0].trim();
			}
		}
		return req.getRemoteAddr();
	}
}
