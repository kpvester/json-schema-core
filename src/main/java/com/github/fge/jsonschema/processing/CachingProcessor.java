package com.github.fge.jsonschema.processing;

import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.exceptions.unchecked.ProcessingError;
import com.github.fge.jsonschema.report.ListProcessingReport;
import com.github.fge.jsonschema.report.LogLevel;
import com.github.fge.jsonschema.report.MessageProvider;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.util.equivalence.Equivalences;
import com.google.common.base.Equivalence;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.ExecutionException;

import static com.github.fge.jsonschema.messages.ProcessingErrors.*;

/**
 * A class caching the result of a {@link Processor}
 *
 * <p>You can use this over whichever processor of your choice. Internally, it
 * uses a {@link LoadingCache} to store results.</p>
 *
 * <p>You can optionally pass an {@link Equivalence} as an argument for cache
 * keys. By default, {@link Equivalences#equals()} will be used.</p>
 *
 * @param <IN> input type for that processor
 * @param <OUT> output type for that processor
 */
public final class CachingProcessor<IN extends MessageProvider, OUT extends MessageProvider>
    implements Processor<IN, OUT>
{
    /**
     * The wrapped processor
     */
    private final Processor<IN, OUT> processor;

    /**
     * The equivalence to use
     */
    private final Equivalence<IN> equivalence;

    /**
     * The cache
     */
    private final LoadingCache<Equivalence.Wrapper<IN>, Output<OUT>> cache;

    /**
     * Constructor
     *
     * <p>This is equivalent to calling {@link #CachingProcessor(Processor,
     * Equivalence)} with {@link Equivalences#equals()} as the second argument.
     * </p>
     *
     * @param processor the processor
     */
    public CachingProcessor(final Processor<IN, OUT> processor)
    {
        this(processor, Equivalences.<IN>equals());
    }

    /**
     * Main constructor
     *
     * @param processor the processor
     * @param equivalence an equivalence to use for cache keys
     */
    public CachingProcessor(final Processor<IN, OUT> processor,
        final Equivalence<IN> equivalence)
    {
        if (processor == null)
            throw new ProcessingError(new ProcessingMessage()
                .message(NULL_PROCESSOR));
        if (equivalence == null)
            throw new ProcessingError(new ProcessingMessage()
                .message(NULL_EQUIVALENCE));
        this.processor = processor;
        this.equivalence = equivalence;
        cache = CacheBuilder.newBuilder().build(loader());
    }

    @Override
    public OUT process(final ProcessingReport report, final IN input)
        throws ProcessingException
    {
        final Output<OUT> cached;
        try {
            cached = cache.get(equivalence.wrap(input));
        } catch (ExecutionException e) {
            throw (ProcessingException) e.getCause();
        }
        report.mergeWith(cached.report);
        return cached.value;
    }

    private CacheLoader<Equivalence.Wrapper<IN>, Output<OUT>> loader()
    {
        return new CacheLoader<Equivalence.Wrapper<IN>, Output<OUT>>()
        {
            @Override
            public Output<OUT> load(final Equivalence.Wrapper<IN> key)
                throws ProcessingException
            {
                final IN input = key.get();
                final ListProcessingReport report
                    = new ListProcessingReport(LogLevel.DEBUG, LogLevel.NONE);
                final OUT value = processor.process(report, input);
                return new Output<OUT>(report, value);
            }
        };
    }

    private static final class Output<T>
    {
        private final ListProcessingReport report;
        private final T value;

        private Output(final ListProcessingReport report, final T value)
        {
            this.report = report;
            this.value = value;
        }
    }

    @Override
    public String toString()
    {
        return "CACHED[" + processor + ']';
    }
}
