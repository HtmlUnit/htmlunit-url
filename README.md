# HtmlUnit - URL

This is a Java implementation of the [WHATWG URL Living Standard](https://url.spec.whatwg.org/).

The main advantage of the WHATWG URL standard is that it fixes the various shortcomings and quirks
of `java.net.URL`, RFC 3986, RFC 3987, etc.

* parse URLs into an easy-to-use representation
* read and set individual URL components (scheme, host, port, path, query, fragment, …)
* work with URL search parameters via a dedicated `UrlSearchParams` API
* report validation errors and failures as defined by the WHATWG specification

This is the code repository of the URL support used by HtmlUnit.

The library was forked from the [whatwg-url](https://github.com/stephanebastian/whatwg-url) project
by [Stephane Bastian](https://github.com/stephanebastian).  
The code has been adapted to match HtmlUnit's code style rules and the Gson dependency has been
removed.  
The code is being expanded, restructured and improved primarily to meet the requirements of this
project.

It is in sync with
[this specific WHATWG commit (27 September 2023)](https://github.com/whatwg/url/commit/aa64bb27d427cef0d87f134980ac762cced1f5bb).

[![Maven Central Version](https://img.shields.io/maven-central/v/org.htmlunit/htmlunit-url)](https://central.sonatype.com/artifact/org.htmlunit/htmlunit-url)

:heart: [Sponsor](https://github.com/sponsors/rbri)

### Project News

**[Developer Blog](https://htmlunit.github.io/htmlunit-blog/)**

[HtmlUnit@mastodon](https://fosstodon.org/@HtmlUnit) | [HtmlUnit@bsky](https://bsky.app/profile/htmlunit.bsky.social) | [HtmlUnit@Twitter](https://twitter.com/HtmlUnit)

### Latest release Version 4.0.0 / May 2026

### Maven

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.htmlunit</groupId>
    <artifactId>htmlunit-url</artifactId>
    <version>4.0.0</version>
</dependency>
```

### Gradle

Add to your `build.gradle`:

```groovy
implementation group: 'org.htmlunit', name: 'htmlunit-url', version: '4.0.0'
```

## A Note on the WHATWG URL Standard

The [WHATWG URL Living Standard](https://url.spec.whatwg.org/) is the authoritative reference for
how URLs should be parsed and serialized across browsers. It supersedes RFC 3986, RFC 3987 and the
older `java.net.URL` behavior and fixes many long-standing inconsistencies.

The library is rather slim (~55 KB), has only one dependency (on
[ICU4J](https://unicode-org.github.io/icu/userguide/icu4j/)) and ships with 3 000+ tests.
Some test data are borrowed from
[Web Platform Tests](https://github.com/web-platform-tests/wpt/tree/master/url/resources/),
the cross-browser test suite used by Safari, Chrome, Firefox and Edge.

As a side note, there is a basic benchmark (built with JMH) that iterates over 500+ typical URLs
and measures throughput (~350 000 ops/s on an AMD Ryzen 5, but your mileage may vary).

### Parse a URL

```java
import org.htmlunit.url.Url;

Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
System.out.println(url.hash());         // #hash1
System.out.println(url.host());         // www.myurl.com
System.out.println(url.hostname());     // www.myurl.com
System.out.println(url.href());         // http://www.myurl.com/path1?a=1&b=2#hash1
System.out.println(url.origin());       // http://www.myurl.com
System.out.println(url.password());     //
System.out.println(url.pathname());     // /path1
System.out.println(url.port());         //
System.out.println(url.protocol());     // http:
System.out.println(url.search());       // ?a=1&b=2
System.out.println(url.searchParams()); // a=1&b=2
System.out.println(url.username());     //
```

### Parse a Relative URL

```java
import org.htmlunit.url.Url;

Url url = Url.create("path1?a=1&b=2#hash1", "http://www.myurl.com/path2?c=3&d=2#hash2");
System.out.println(url.hash());         // #hash1
System.out.println(url.host());         // www.myurl.com
System.out.println(url.hostname());     // www.myurl.com
System.out.println(url.href());         // http://www.myurl.com/path1?a=1&b=2#hash1
System.out.println(url.origin());       // http://www.myurl.com
System.out.println(url.password());     //
System.out.println(url.pathname());     // /path1
System.out.println(url.port());         //
System.out.println(url.protocol());     // http:
System.out.println(url.search());       // ?a=1&b=2
System.out.println(url.searchParams()); // a=1&b=2
System.out.println(url.username());     //
```

### Set URL Properties

```java
import org.htmlunit.url.Url;

Url url = Url.create("http://www.myurl.com/path1?a=1&b=2#hash1");
// hash
System.out.println(url.hash());         // #hash1
url.hash("hash2");
System.out.println(url.hash());         // #hash2
// host
System.out.println(url.host());         // www.myurl.com
url.host("anotherhost.io");
System.out.println(url.host());         // anotherhost.io
// set other properties such as username, password, pathname, port, protocol, etc.
```

### Validation Errors

The specification defines [ValidationError](https://url.spec.whatwg.org/#validation-error).
A validation error does not stop processing the URL unless it is also a failure.

If a failure occurs when calling `Url.create(…)` a `ValidationException` is thrown.

However, if a failure occurs when calling a setter, an exception is **not thrown** — the
`ValidationError` is instead added to `Url.validationErrors()`.

```java
import org.htmlunit.url.Url;
import org.htmlunit.url.ValidationError;

// failure during creation → ValidationException thrown
Url url = Url.create("https://exa%23mple.org"); // throws ValidationException

// failure during a setter → recorded in validationErrors()
Url url2 = Url.create("http://www.myurl.com");
url2.host("1.2.3.4.5");
System.out.println(url2.validationErrors().get(0)); // IPV4_TOO_MANY_PARTS
```

### Java API Reference

```java
public interface Url {
    static boolean canParse(String url);
    static boolean canParse(String url, String baseUrl);
    static Url create();
    static Url create(String input);
    static Url create(String input, String baseUrl);

    String hash();
    Url    hash(String value);
    String host();
    Url    host(String value);
    String hostname();
    Url    hostname(String value);
    String href();
    Url    href(String value);
    String origin();
    String password();
    Url    password(String value);
    String pathname();
    Url    pathname(String value);
    String port();
    Url    port(String value);
    String protocol();
    Url    protocol(String value);
    String search();
    Url    search(String value);
    UrlSearchParams searchParams();
    String toJSON();
    String username();
    Url    username(String value);

    // not in the spec, but very useful to list validation errors when parsing
    // the initial raw URL or when setting properties.
    List<ValidationError> validationErrors();
}

public interface UrlSearchParams {
    UrlSearchParams      append(String name, String value);
    Collection<String>   delete(String name);
    boolean              delete(String name, String value);
    UrlSearchParams      entries(BiConsumer<String, String> consumer);
    String               get(String name);
    Collection<String>   getAll(String name);
    boolean              has(String name);
    boolean              has(String name, String value);
    UrlSearchParams      set(String name, String value);
    int                  size();
    UrlSearchParams      sort();
}

public enum ValidationError {
    // ... various enum constants ...

    String  description();
    boolean isFailure();
}
```

### Last CI build

The latest builds are available from our
[Jenkins CI build server](https://jenkins.wetator.org/job/HtmlUnit%20-%20URL/ "HtmlUnit - URL CI")

[![Build Status](https://jenkins.wetator.org/buildStatus/icon?job=HtmlUnit+-+URL)](https://jenkins.wetator.org/job/HtmlUnit%20-%20URL/)

If you use Maven please add:

    <dependency>
        <groupId>org.htmlunit</groupId>
        <artifactId>htmlunit-url</artifactId>
        <version>5.0.0-SNAPSHOT</version>
    </dependency>

You have to add the Sonatype Central snapshot repository to the `repositories` section of your
`pom.xml`:

    <repositories>
        <repository>
            <name>Central Portal Snapshots</name>
            <id>central-portal-snapshots</id>
            <url>https://central.sonatype.com/repository/maven-snapshots/</url>
            <releases>
                <enabled>false</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>

## Start HtmlUnit - URL Development

These instructions will get you a copy of the project up and running on your local machine for
development and testing purposes.

### Prerequisites

You need:
* Java 17 or later
* A local Maven installation

### Building

Create a local clone of the repository and you are ready to start.

Open a command line window from the root folder of the project and call

```
mvn compile
```

### Running the Tests

```
mvn test
```

## Contributing

Pull Requests and all other Community Contributions are essential for open source software.
Every contribution — from bug reports to feature requests, typos to full new features — is greatly
appreciated.

## Deployment and Versioning

This part is intended for committers who are packaging a release.

* Check all your files are checked in
* Execute these Maven commands to be sure all tests are passing and everything is up to date

```
mvn versions:display-plugin-updates
mvn versions:display-dependency-updates
mvn -U clean test
```

* Update the version number in `pom.xml` and `README.md`
* Commit the changes

* Build and deploy the artifacts

```
mvn -up clean deploy
```

* Go to [Maven Central Portal](https://central.sonatype.com/) and process the deploy
  - publish the package and wait until it is processed

* Create the version on GitHub
    * Login to GitHub and open the project https://github.com/HtmlUnit/htmlunit-url
    * Click Releases > Draft new release
    * Fill the tag and title fields with the release number (e.g. 4.0.0)
    * Append
        * htmlunit-url-4.x.x.jar
        * htmlunit-url-4.x.x.jar.asc
        * htmlunit-url-4.x.x.pom
        * htmlunit-url-4.x.x.pom.asc
        * htmlunit-url-4.x.x-javadoc.jar
        * htmlunit-url-4.x.x-javadoc.jar.asc
        * htmlunit-url-4.x.x-sources.jar
        * htmlunit-url-4.x.x-sources.jar.asc
    * And publish the release

* Update the version number in `pom.xml` to start the next snapshot development
* Update the HtmlUnit `pom.xml` to use the new release

## Authors

* **RBRi**
* **Stephane Bastian** and all contributors to [whatwg-url](https://github.com/stephanebastian/whatwg-url)

## License

This project is licensed under the Apache 2.0 License

## Acknowledgments

Many thanks to all of you contributing to
[whatwg-url](https://github.com/stephanebastian/whatwg-url) in the past.

### Development Tools

Special thanks to:

<a href="https://www.jetbrains.com/community/opensource/"><img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg" alt="JetBrains" width="42"></a>
<a href="https://www.jetbrains.com/idea/"><img src="https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA_icon.svg" alt="IntelliJ IDEA" width="42"></a>  
**[JetBrains](https://www.jetbrains.com/)** for providing IntelliJ IDEA under their
[open source development license](https://www.jetbrains.com/community/opensource/) and

<a href="https://www.eclipse.org/"><img src="https://www.eclipse.org/eclipse.org-common/themes/solstice/public/images/logo/eclipse-foundation-grey-orange.svg" alt="Eclipse Foundation" width="80"></a>  
Eclipse Foundation for their Eclipse IDE

<a href="https://www.syntevo.com/smartgit/"><img src="https://www.syntevo.com/assets/images/logos/smartgit-8c1aa1e2.svg" alt="SmartGit" width="54"></a>  
to **[Syntevo](https://www.syntevo.com/)** for their excellent [SmartGit](https://www.syntevo.com/smartgit/)!