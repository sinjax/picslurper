package org.openimaj.picslurper;

import java.net.URL;

import org.openimaj.image.MBFImage;

public interface SiteSpecificConsumer {
	public boolean canConsume(URL url);
	public MBFImage consume(URL url);
}
