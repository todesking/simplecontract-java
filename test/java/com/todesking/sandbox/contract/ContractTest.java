package com.todesking.sandbox.contract;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ContractTest {
	private static final String FUGA = "fuga";
	private static final String HOGE = "hoge";

	public static interface Storage {
		public int size();

		public void put(String name, String value);

		public boolean contains(String name);

		public String get(String name);

		public void doNothing();

		public static class ContractForImplement extends Contract<Storage> {
			public void size(int result) {
				contract("0以上の値を返すこと", result >= 0);
			}

			public void put(Void _, String name, String value) {
				contract("putされたキーはcontainsがtrueになること", target().contains(name));
				contract("putされた値はgetで取得できること", StringUtils.equals(target()
					.get(name), value));
			}
		}

		public static class ContractForClient extends Contract<Storage> {
			public void put(String name, String value) {
				contract("nameはnullでないこと", name != null);
				contract("valueはnullでないこと", value != null);
			}

			public void get(String name) {
				contract("nameはnullでないこと", name != null);
				contract("contains(name)がtrueであること", target().contains(name));
			}
		}
	}

	@Mock
	public Storage storage;

	@Before
	public void before() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void test_contract_forClient() throws Exception {
		final Storage target =
			Contract.checkerForClient(Storage.class, storage);

		// valid usage
		target.put(HOGE, FUGA);

		try {
			target.put(HOGE, null);
			fail();
		} catch (ContractError e) {
			assertThat(e.getMessage(), is(containsString("valueはnullでないこと")));
		}

		when(storage.contains(HOGE)).thenReturn(true);
		target.get(HOGE);

		when(storage.contains(HOGE)).thenReturn(false);
		try {
			target.get(HOGE);
			fail();
		} catch (ContractError e) {
			assertThat(
				e.getMessage(),
				is(containsString("contains(name)がtrueであること")));
		}
	}

	@Test
	public void test_contract_forImplement() throws Exception {
		final Storage target =
			Contract.checkerForImplementation(Storage.class, storage);

		// success invoke method with no contract
		target.doNothing();

		when(storage.size()).thenReturn(0);
		assertThat(target.size(), is(0));
		when(storage.size()).thenReturn(-1);
		try {
			target.size(); // -1
			fail("contract not checked");
		} catch (ContractError e) {
			assertThat(e.getMessage(), is(containsString("0以上の値を返すこと")));
		}

		when(storage.contains(HOGE)).thenReturn(false);
		try {
			target.put(HOGE, FUGA);
			fail();
		} catch (ContractError e) {
			assertThat(
				e.getMessage(),
				is(containsString("putされたキーはcontainsがtrueになること")));
		}
		when(storage.contains(HOGE)).thenReturn(true);
		when(storage.get(HOGE)).thenReturn(null);
		try {
			target.put(HOGE, FUGA);
			fail();
		} catch (ContractError e) {
			assertThat(
				e.getMessage(),
				is(containsString("putされた値はgetで取得できること")));
		}

		when(storage.contains(HOGE)).thenReturn(true);
		when(storage.get(HOGE)).thenReturn(FUGA);
		target.put(HOGE, FUGA); // should success
	}
}
