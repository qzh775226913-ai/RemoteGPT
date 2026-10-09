"""Read only the specified rollout; return question/control metadata, never chat text."""
import json
import os
import sys


def scan(path, offset):
    size = os.path.getsize(path)
    reset = offset > size
    offset = 0 if reset else max(0, offset)
    records, context = [], None
    with open(path, 'rb') as stream:
        stream.seek(offset)
        start = offset
        while stream.tell() - start < 16 * 1024 * 1024:
            position = stream.tell()
            line = stream.readline(16 * 1024 * 1024)
            if not line:
                break
            if not line.endswith(b'\n'):
                if stream.tell() == size:
                    stream.seek(position)  # Wait for a complete record, including split UTF-8.
                    break
                while line and not line.endswith(b'\n'):
                    line = stream.readline(16 * 1024 * 1024)
                continue
            try:
                record = json.loads(line)
            except (ValueError, UnicodeError):
                continue
            payload = record.get('payload', {})
            kind = payload.get('type')
            if record.get('type') == 'turn_context':
                sandbox = payload.get('sandbox_policy')
                context = {'turn_id': payload.get('turn_id'), 'approval_policy': payload.get('approval_policy'),
                           'sandbox_policy': {'type': sandbox.get('type')} if isinstance(sandbox, dict) else None}
            elif record.get('type') == 'event_msg' and kind in ('task_started', 'user_message', 'task_interrupted'):
                if kind in ('task_started', 'user_message'):
                    records = []
                records.append({'type': kind, 'turn_id': payload.get('turn_id', '')})
            elif record.get('type') == 'response_item' and kind == 'function_call' and payload.get('name') in ('request_user_input_async', 'functions.request_user_input_async'):
                arguments = payload.get('arguments', '')
                if isinstance(arguments, str) and len(arguments.encode('utf-8')) <= 12000:
                    records.append({'type': kind, 'call_id': payload.get('call_id', ''), 'arguments': arguments})
            elif record.get('type') == 'response_item' and kind == 'function_call_output':
                try:
                    output = json.loads(payload.get('output', ''))
                    if isinstance(output, dict) and output.get('accepted') is True:
                        records.append({'type': kind, 'call_id': payload.get('call_id', ''), 'accepted': True})
                except (ValueError, TypeError):
                    pass
            if len(json.dumps(records, ensure_ascii=False).encode('utf-8')) > 45000:
                records.pop()
                stream.seek(position)
                break
        offset = stream.tell()
    return {'offset': offset, 'size': size, 'reset': reset, 'records': records, 'context': context}


if __name__ == '__main__':
    args = json.loads(sys.argv[1])
    print(json.dumps(scan(args['path'], int(args.get('offset', 0))), ensure_ascii=False))
