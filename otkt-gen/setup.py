from setuptools import setup, find_packages

setup(
    name="otkt",
    version="1.0.0",
    description="Opentelemetry to Kieker translator for Python",
    url="https://github.com/kieker-monitoring/OtktDSL",
    packages=find_packages(),
    install_requires=[
        "opentelemetry-api==1.18.0",
        "opentelemetry-sdk==1.18.0",
        "opentelemetry-exporter-otlp==1.18.0",
        "opentelemetry-instrumentation==0.40b0",
        "kiekerforpython",
    ],
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
    ],
)
