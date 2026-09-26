.PHONY: all install build clean

all: install build

install:
	pip install .

build:
	cd collector && mvn clean package

run:
	java -jar collector/target/collector-jar-with-dependencies.jar -c config.txt

clean:
	pip uninstall otkt
	cd collector && mvn clean
