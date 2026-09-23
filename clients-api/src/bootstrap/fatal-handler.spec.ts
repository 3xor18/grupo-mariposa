import { EventEmitter } from 'node:events';
import {
  exitFatally,
  FATAL_EXIT_CODE,
  fatalLine,
  processFatalSink,
  registerFatalHandlers,
} from './fatal-handler';

describe('fatal handler', () => {
  const now = new Date('2026-09-22T10:00:00.000Z');

  it('should_render_structured_fatal_line_with_stack_for_errors', () => {
    const error = new Error('boom');

    expect(JSON.parse(fatalLine(error, now))).toEqual({
      level: 'fatal',
      timestamp: '2026-09-22T10:00:00.000Z',
      service: 'clients-api',
      message: 'Error: boom',
      stack: error.stack,
    });
  });

  it('should_render_non_errors_without_stack', () => {
    expect(fatalLine('plain', now)).toBe(
      '{"level":"fatal","timestamp":"2026-09-22T10:00:00.000Z","service":"clients-api",' +
        '"message":"plain"}\n',
    );
  });

  it('should_render_errors_without_stack', () => {
    const error = new Error('no stack');
    delete error.stack;

    expect(JSON.parse(fatalLine(error, now))).not.toHaveProperty('stack');
  });

  it('should_write_and_exit_with_failure_code', () => {
    const sink = { write: jest.fn(), exit: jest.fn() };

    exitFatally(new Error('fatal'), sink);

    expect(sink.write).toHaveBeenCalledWith(expect.stringContaining('"message":"Error: fatal"'));
    expect(sink.exit).toHaveBeenCalledWith(FATAL_EXIT_CODE);
  });

  it('should_route_unhandled_rejections_and_exceptions_to_the_fatal_handler', () => {
    const source = new EventEmitter();
    const onFatal = jest.fn();

    registerFatalHandlers(source as unknown as NodeJS.Process, onFatal);
    source.emit('unhandledRejection', 'rejected');
    source.emit('uncaughtException', new Error('thrown'));

    expect(onFatal.mock.calls).toEqual([['rejected'], [new Error('thrown')]]);
  });

  it('should_write_to_stderr_and_exit_the_process_by_default', () => {
    const write = jest.spyOn(process.stderr, 'write').mockImplementation(() => true);
    const exit = jest.spyOn(process, 'exit').mockImplementation(() => undefined as never);

    exitFatally('default sink');

    expect(write).toHaveBeenCalledWith(expect.stringContaining('default sink'));
    expect(exit).toHaveBeenCalledWith(FATAL_EXIT_CODE);
    expect(processFatalSink.write).toBeInstanceOf(Function);
    write.mockRestore();
    exit.mockRestore();
  });
});
