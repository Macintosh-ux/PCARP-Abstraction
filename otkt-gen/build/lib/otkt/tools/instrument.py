from opentelemetry import trace
from otkt.tools.javaMapping import signature

# Create a OTel tracer
tracer = trace.get_tracer(__name__)
def instrument(func):
	attributes = { "ess": 0 }
	def instrument_func(*args, **kwargs):
		with tracer.start_as_current_span("OTelSpan", attributes=attributes) as foo:
			# We use module.func_name of Python program mapped as Java's fully qualified signature
			foo.set_attribute("operation_signature", signature(func))
			# session_id is only relevant with Kieker agent on Java applications
			foo.set_attribute("session_id", "<no-session-id>")
			# Target application should provide hostname
			foo.set_attribute("hostname", "localhost")
			result = func(*args, **kwargs)
			return result
	return instrument_func
