/*
 * Copyright 2023 - Stephane Bastian - stephane.bastian.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.htmlunit.url.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.htmlunit.url.Url;
import org.htmlunit.url.UrlSearchParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link UrlSearchParams} API.
 *
* @author Stephane Bastian
* @author Ronald Brill
 */
public class TestUrlSearchParamsApi {
  @Test
  public void append() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2");
    url.searchParams().append("c", "3");
    Assertions.assertEquals("http://www.myurl.com/path1?a=1&b=2&c=3", url.href());
    url.searchParams().append("b", "4");
    Assertions.assertEquals("http://www.myurl.com/path1?a=1&b=2&c=3&b=4", url.href());
    url.searchParams().append("a", "5");
    Assertions.assertEquals("http://www.myurl.com/path1?a=1&b=2&c=3&b=4&a=5", url.href());
  }

  @Test
  public void deleteByName() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2");
    url.searchParams().delete("a");
    Assertions.assertEquals("http://www.myurl.com/path1?b=2", url.href());
  }

  @Test
  public void deleteByNameAndValue() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    url.searchParams().delete("a", "2");
    Assertions.assertEquals("http://www.myurl.com/path1?a=1&b=2", url.href());
    url.searchParams().delete("a", "1");
    Assertions.assertEquals("http://www.myurl.com/path1?b=2", url.href());
    url.searchParams().delete("b", "2");
    Assertions.assertEquals("http://www.myurl.com/path1", url.href());
  }

  @Test
  public void entries() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    List<String[]> paramsCollector = new ArrayList<>();
    url.searchParams().entries((name, value) -> {
      paramsCollector.add(new String[] {name, value});
    });
    Assertions.assertEquals(3, paramsCollector.size());
    Assertions.assertEquals("a", paramsCollector.get(0)[0]);
    Assertions.assertEquals("1", paramsCollector.get(0)[1]);
    Assertions.assertEquals("b", paramsCollector.get(1)[0]);
    Assertions.assertEquals("2", paramsCollector.get(1)[1]);
    Assertions.assertEquals("a", paramsCollector.get(2)[0]);
    Assertions.assertEquals("2", paramsCollector.get(2)[1]);
  }

  @Test
  public void get() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    Assertions.assertEquals("1", url.searchParams().get("a"));
    Assertions.assertEquals("2", url.searchParams().get("b"));
  }

  @Test
  public void getAll() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    Assertions.assertEquals(Arrays.asList("1", "2"), url.searchParams().getAll("a"));
    Assertions.assertEquals(Arrays.asList("2"), url.searchParams().getAll("b"));
  }

  @Test
  public void hasName() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    Assertions.assertTrue(url.searchParams().has("a"));
    Assertions.assertTrue(url.searchParams().has("b"));
    Assertions.assertFalse(url.searchParams().has("c"));
  }

  @Test
  public void hasNameAndValue() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    Assertions.assertTrue(url.searchParams().has("a", "1"));
    Assertions.assertTrue(url.searchParams().has("a", "2"));
    Assertions.assertFalse(url.searchParams().has("a", "3"));
    Assertions.assertFalse(url.searchParams().has("e", "1"));
  }

  @Test
  public void set() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    url.searchParams().set("a", "5");
    Assertions.assertEquals("http://www.myurl.com/path1?a=5&b=2", url.href());
    url.searchParams().set("c", "4");
    Assertions.assertEquals("http://www.myurl.com/path1?a=5&b=2&c=4", url.href());
  }

  @Test
  public void size() {
    Url url = Url.create("http://www.myurl.com/path1?a=1&b=2&a=2");
    Assertions.assertEquals(3, url.searchParams().size());
  }

  @Test
  public void sort() {
    Url url = Url.create("http://www.myurl.com/path1?z=26&a=3&b=2&a=1");
    url.searchParams().sort();
    Assertions.assertEquals("http://www.myurl.com/path1?a=3&a=1&b=2&z=26", url.href());
  }
}