package org.springframework.boot.repl;

import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cli.Command;
import org.springframework.boot.cli.CommandFactory;
import org.springframework.boot.cli.OptionHelp;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * @author Jon Brisbin
 */
public class CommandCompleter extends StringsCompleter {

	private static final Logger LOG = LoggerFactory.getLogger(CommandCompleter.class);

	private final Map<String, Completer> optionCompleters = new HashMap<>();
	private       List<Command>          commands         = new ArrayList<>();
	private ConsoleReader console;
	private String        lastBuffer;

	public CommandCompleter(ConsoleReader console) {
		this.console = console;

		for(CommandFactory fac : ServiceLoader.load(CommandFactory.class, getClass().getClassLoader())) {
			commands.addAll(fac.getCommands());
		}

		List<String> names = new ArrayList<>();
		for(Command c : commands) {
			names.add(c.getName());
			List<String> opts = new ArrayList<>();
			for(OptionHelp optHelp : c.getOptionsHelp()) {
				opts.addAll(optHelp.getOptions());
			}
			optionCompleters.put(c.getName(), new ArgumentCompleter(
					new StringsCompleter(c.getName()),
					new StringsCompleter(opts),
					new NullCompleter()
			));
		}
		getStrings().addAll(names);
	}

	@Override
	public int complete(String buffer, int cursor, List<CharSequence> candidates) {
		int i = super.complete(buffer, cursor, candidates);
		if(buffer.indexOf(' ') < 1) {
			return i;
		}
		String name = buffer.substring(0, buffer.indexOf(' '));
		if("".equals(name.trim())) {
			return i;
		}
		for(Command c : commands) {
			if(!c.getName().equals(name)) {
				continue;
			}
			if(buffer.equals(lastBuffer)) {
				lastBuffer = buffer;
				try {
					console.println();
					console.println("Usage:");
					console.println(c.getName() + " " + c.getUsageHelp());
					List<List<String>> rows = new ArrayList<>();
					int maxSize = 0;
					for(OptionHelp optHelp : c.getOptionsHelp()) {
						List<String> cols = new ArrayList<>();
						for(String s : optHelp.getOptions()) {
							cols.add(s);
						}
						String opts = StringUtils.collectionToDelimitedString(cols, "|");
						if(opts.length() > maxSize) {
							maxSize = opts.length();
						}
						cols.clear();
						cols.add(opts);
						cols.add(optHelp.getUsageHelp());
						rows.add(cols);
					}

					StringBuilder sb = new StringBuilder("\t");
					for(List<String> row : rows) {
						String col1 = row.get(0);
						String col2 = row.get(1);
						for(int j = 0; j < (maxSize - col1.length()); j++) {
							sb.append(" ");
						}
						sb.append(col1).append(": ").append(col2);
						console.println(sb.toString());
						sb = new StringBuilder("\t");
					}

					console.drawLine();
				} catch(IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}
			Completer completer = optionCompleters.get(c.getName());
			if(null != completer) {
				i = completer.complete(buffer, cursor, candidates);
				break;
			}
		}

		lastBuffer = buffer;
		return i;
	}

}
