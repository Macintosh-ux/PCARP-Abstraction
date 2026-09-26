from opentelemetry.sdk.trace import SpanProcessor
from opentelemetry import trace
from opentelemetry.trace import Span
import threading

span_registry = {}

_eoi_trace_registry = {}
class IncrementAttributeSpanProcessor(SpanProcessor):
	_lock = threading.Lock()

	def __init__(self):
		pass

	def on_start(self, span: Span, parent_context):
		global span_registry
		global _eoi_trace_registry
		span_id = span.get_span_context().span_id
		trace_id = span.get_span_context().trace_id
		span_registry[span_id] = span
		
			
		if trace_id not in _eoi_trace_registry:
			_eoi_trace_registry[span.get_span_context().trace_id] = 0
		
		with self.__class__._lock:
			
			global _eoi
			span.set_attribute("eoi",_eoi_trace_registry[trace_id])
			_eoi_trace_registry[trace_id]+=1
		
		parent_span_context = span.parent
		if parent_span_context is not None:
			# Get the attribute value from the parent span
			parent_span = span_registry[parent_span_context.span_id]
			current_value_ess = parent_span.attributes["ess"]
			# Get the current value of the attribute in the current span
			current_value_ess = current_value_ess+1
			# Increment the current span's attribute value
			span.set_attribute("ess",current_value_ess)
		else:
			pass


	def on_end(self, span: Span):
		pass
