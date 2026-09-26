
import kieker.analysis.generic.sink.DataSink;
import kieker.analysis.generic.source.rewriter.NoneTraceMetadataRewriter;
import kieker.analysis.generic.source.tcp.MultipleConnectionTcpSourceStage;
import kieker.common.exception.ConfigurationException;
import teetime.framework.Configuration;
		
		/**
		 * Analysis configuration for the data collector.
		 *
		 * @author Reiner Jung
		 *
		 * @since 1.15
		 *
		 */
public class CollectorConfiguration extends Configuration {

	private final DataSink consumer;
//	private final ISourceCompositeStage sourceStage;

	/**
	 * Configure analysis.
	 *
	 * @param configuration
	 *            configuration for the collector
	 * @throws ConfigurationException
	 *             on configuration error
	 */
	public CollectorConfiguration(final kieker.common.configuration.Configuration configuration)
			throws ConfigurationException {
		
		final MultipleConnectionTcpSourceStage mySource = new MultipleConnectionTcpSourceStage(4137, 650000, new NoneTraceMetadataRewriter());
		this.consumer = new DataSink(configuration);
		this.connectPorts(mySource.getOutputPort(), this.consumer.getInputPort());
	}

	public DataSink getCounter() {
		return this.consumer;
	}


}