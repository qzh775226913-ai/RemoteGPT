"""Read only the specified rollout; return question/control metadata, never chat text."""
import json
import os
import sys


def scan(path, offset, question_calls=None):
    known = set(question_calls or [])
    size = os.path.getsize(path)
    reset = offset > size
    offset = 0 if reset else max(0, offset)
    records, history, context = [], [], None
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
            if not isinstance(payload, dict):
                continue
            kind = payload.get('type')
            pending, archived = None, None
            if record.get('type') == 'turn_context':
                sandbox = payload.get('sandbox_policy')
                context = {'turn_id': payload.get('turn_id'), 'approval_policy': payload.get('approval_policy'),
                           'sandbox_policy': {'type': sandbox.get('type')} if isinstance(sandbox, dict) else None}
            elif record.get('type') == 'event_msg' and kind in ('task_started', 'user_message', 'task_interrupted'):
                pending = {'type': kind, 'turn_id': payload.get('turn_id', '')}
                if kind == 'task_started':
                    archived = pending
                elif kind == 'user_message':
                    answer = payload.get('message', '')
                    if isinstance(answer, str) and answer.startswith('回答上一轮的问题：\n') and len(answer.encode('utf-8')) <= 24000:
                        archived = {'type': 'user_answer', 'text': answer}
            elif record.get('type') == 'response_item' and kind == 'message' and payload.get('role') == 'user':
                # Some hosts save user input only as a response item, without event_msg.
                pending = {'type': 'user_message', 'turn_id': ''}
                content = payload.get('content', [])
                if isinstance(content, list):
                    answer = '\n'.join(x.get('text', '') for x in content if isinstance(x, dict) and isinstance(x.get('text'), str))
                    if answer.startswith('回答上一轮的问题：\n') and len(answer.encode('utf-8')) <= 24000:
                        archived = {'type': 'user_answer', 'text': answer}
            elif record.get('type') == 'response_item' and kind == 'function_call' and payload.get('name') in ('request_user_input_async', 'functions.request_user_input_async', 'request_user_input', 'functions.request_user_input'):
                arguments = payload.get('arguments', '')
                if isinstance(arguments, str) and len(arguments.encode('utf-8')) <= 12000:
                    known.add(payload.get('call_id', ''))
                    archived = {'type': kind, 'call_id': payload.get('call_id', ''), 'arguments': arguments}
                    if payload.get('name', '').endswith('request_user_input_async'):
                        pending = archived
            elif record.get('type') == 'response_item' and kind == 'function_call_output':
                try:
                    output = json.loads(payload.get('output', ''))
                    if isinstance(output, dict) and output.get('accepted') is True:
                        pending = archived = {'type': kind, 'call_id': payload.get('call_id', ''), 'accepted': True}
                    elif payload.get('call_id', '') in known and isinstance(output, dict) and isinstance(output.get('answers'), dict) and len(json.dumps(output).encode('utf-8')) <= 12000:
                        archived = {'type': kind, 'call_id': payload.get('call_id', ''), 'answers': output['answers']}
                except (ValueError, TypeError):
                    pass
            if pending is None and archived is None:
                continue
            next_records = [] if pending is not None and pending['type'] in ('task_started', 'user_message') else records[:]
            if pending is not None:
                next_records.append(pending)
            next_history = history + ([archived] if archived is not None else [])
            if len(json.dumps([next_records, next_history], ensure_ascii=False).encode('utf-8')) > 45000:
                stream.seek(position)
                break
            records, history = next_records, next_history
        offset = stream.tell()
    return {'offset': offset, 'size': size, 'reset': reset, 'records': records, 'history': history, 'context': context}


if __name__ == '__main__':
    args = json.loads(sys.argv[1])
    print(json.dumps(scan(args['path'], int(args.get('offset', 0)), args.get('questionCalls')), ensure_ascii=False))
