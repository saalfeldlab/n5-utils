/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.saalfeldlab;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class CopyCREMI extends Copy {

	public CopyCREMI(final Options options) {

		super(options);
	}

	@Override
	protected void copyAttributes(final String groupName)
			throws IOException {

		final Map<String, Class<?>> attributes = n5Reader.listAttributes(groupName);
		attributes.forEach(
				(key, clazz) -> {
					try {
						final Object attribute = n5Reader.getAttribute(groupName, key, clazz);
						if (key.equals("resolution") && clazz == double[].class)
							reorderIfNecessary((double[])attribute);
						if (key.equals("offset") && clazz == double[].class)
							reorderIfNecessary((double[])attribute);

						n5Writer.setAttribute(groupName, key, attribute);
					} catch (final IOException e) {
						e.printStackTrace(System.err);
					}
				});
	}

	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException {

		final Options options = new Options(args);

		if (!options.isParsedSuccessfully())
			return;

		final CopyCREMI copy = new CopyCREMI(options);

		final List<String> groupNames = options.getGroupNames();

		if (groupNames == null)
			copy.copyGroup("");
		else {
			for (final String groupName : groupNames)
				if (copy.n5Reader.exists(groupName)) {
					if (copy.n5Reader.datasetExists(groupName))
						copy.copyDataset(groupName);
					else
						copy.copyGroup(groupName);
				}
		}
	}
}
