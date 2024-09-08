package com.enginemachiner.honkytones.client;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/* Based on OpenFM. */

/*
 * An Erroring InputStream when mark limits are exceeded
 * Fixes MP3SPI infinitely reading until it finds a false positive
 */
public class MarkErrorInputStream extends FilterInputStream {

	long pos;	long markLimit;		boolean markActive = false;

	public MarkErrorInputStream( InputStream stream ) {

		super(stream);		if ( stream.markSupported() ) return;

		throw new IllegalArgumentException("Stream does not support mark.");

	}

	private long limit(long n) throws IOException {

		if ( !markActive ) return n;	long length = markLimit - pos;

		if ( length <= 0 ) {

			in.reset(); throw new IOException("Limit exceeded.");

		} else if ( length < n ) return length;

		return n;

	}

	private void addPos(long n) {

		if ( n < 0 || !markActive ) return;

		pos += n;

	}

	@Override
	public int read() throws IOException {

		limit(1);		int i = super.read();

		addPos(1);	return i;

	}

	@Override
	public int read( byte[] bytes ) throws IOException {

		int length = (int) limit( bytes.length );

		int i = super.read( bytes, 0, length );

		addPos(i);	return i;

	}

	@Override
	public int read( byte @NotNull [] bytes, int offset, int length ) throws IOException {

		length = (int) limit(length);

		int i = super.read( bytes, offset, length );

		addPos(i);	return i;

	}

	@Override
	public long skip(long s) throws IOException {

		s = limit(s);	long i = super.skip(s);

		addPos(i);		return i;
	}

	@Override
	public synchronized void mark( int readLimit ) {

		// Prevent mp3spi's insane limit of 4096001.

		if ( readLimit == 4096001 ) readLimit = 4096;

		super.mark(readLimit);

		markActive = true;	markLimit = readLimit;	pos = 0;

	}

	@Override
	public synchronized void reset() throws IOException {
		super.reset();		markActive = false;
	}

	@Override
	public int available() throws IOException {

		// Allows BufferedInputStream to stop reading.
		if ( pos >= markLimit && markActive ) return 0;

		return (int) limit( super.available() );

	}
	
}
