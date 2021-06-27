# This workflow will build a Java project with Maven
# For more information see: https://help.github.com/actions/language-and-framework-guides/building-and-testing-java-with-maven

name: Continuous Integration

on:
  push:
    branches: [ master, develop, v1.x-develop, v1.X ]
  pull_request:
    branches: [ develop, v1.x-develop ]

jobs:
  unix:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest]
        java: [8]
    steps:
      - name: Cache Maven Repos
        uses: actions/cache@v2
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-maven-
      - uses: actions/checkout@v2
      - name: Set up JDK ${{ matrix.java }}
        uses: actions/setup-java@v1
        with:
          java-version: ${{ matrix.java }}
      - name: Check with Maven
        run: mvn -B clean package apache-rat:check findbugs:findbugs -Dmaven.test.skip=true
      - name: Build with Maven
        run: mvn -Prelease-nacos -Dmaven.test.skip=true clean install -U
      - name: Test With Maven
        run: mvn -Prelease-nacos clean test -DforkCount=0
