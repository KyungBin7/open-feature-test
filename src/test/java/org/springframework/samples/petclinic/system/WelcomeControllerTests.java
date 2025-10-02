/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.system;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WelcomeController 테스트
 */
@WebMvcTest(WelcomeController.class)
class WelcomeControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private OpenFeatureAPI openFeatureAPI;

	@MockBean
	private Client client;

	/**
	 * Variant A (기존 디자인) 테스트
	 */
	@Test
	void shouldShowVariantA() throws Exception {
		// Given
		when(openFeatureAPI.getClient()).thenReturn(client);
		when(client.getBooleanValue(eq("welcome-page-redesign"), eq(false), any())).thenReturn(false);

		// When & Then
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(view().name("welcome"))
			.andExpect(model().attribute("useNewDesign", false))
			.andExpect(model().attribute("version", "v1"))
			.andExpect(model().attributeExists("userId"));
	}

	/**
	 * Variant B (새 디자인) 테스트
	 */
	@Test
	void shouldShowVariantB() throws Exception {
		// Given
		when(openFeatureAPI.getClient()).thenReturn(client);
		when(client.getBooleanValue(eq("welcome-page-redesign"), eq(false), any())).thenReturn(true);

		// When & Then
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(view().name("welcome"))
			.andExpect(model().attribute("useNewDesign", true))
			.andExpect(model().attribute("version", "v2"))
			.andExpect(model().attributeExists("userId"));
	}

	/**
	 * 사용자 ID 쿠키 생성 테스트
	 */
	@Test
	void shouldCreateUserIdCookie() throws Exception {
		// Given
		when(openFeatureAPI.getClient()).thenReturn(client);
		when(client.getBooleanValue(anyString(), anyBoolean(), any())).thenReturn(false);

		// When & Then
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(cookie().exists("petclinic_user_id"))
			.andExpect(cookie().maxAge("petclinic_user_id", 5 * 60)) // 5분
			.andExpect(cookie().httpOnly("petclinic_user_id", true));
	}

}
