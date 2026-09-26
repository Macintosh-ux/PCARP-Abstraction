from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, SimpleSpanProcessor

from otkt.kieker.KiekerExporter import KiekerTcpExporter
from otkt.kieker.KiekerProcessor import IncrementAttributeSpanProcessor

trace.set_tracer_provider(TracerProvider())

# Use the gRPC OTLP exporter (ensure the endpoint is correct for gRPC)
otlp_exporter = KiekerTcpExporter()

# Set up the span processor
span_processor = SimpleSpanProcessor(otlp_exporter)
span_processor_2 = IncrementAttributeSpanProcessor()

trace.get_tracer_provider().add_span_processor(span_processor)
trace.get_tracer_provider().add_span_processor(span_processor_2)
# Create a tracer
tracer = trace.get_tracer(__name__)
