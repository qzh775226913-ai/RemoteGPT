import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('conversation_state', Path(__file__).resolve().parents[1] / 'app/src/main/assets/conversation_state.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ConversationStateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'rollout.jsonl'
        self.path.touch()

    def add(self, kind, payload):
        with self.path.open('a') as stream:
            stream.write(json.dumps({'type': kind, 'payload': payload}, ensure_ascii=False) + '\n')

    def test_scanner_returns_control_metadata_without_chat_or_full_policy(self):
        self.add('event_msg', {'type': 'task_started', 'turn_id': 'test'})
        self.add('turn_context', {'turn_id': 'test', 'approval_policy': 'never', 'sandbox_policy': {'type': 'danger-full-access', 'private_path': '/private'}, 'developer_instructions': 'private-instructions'})
        self.add('response_item', {'type': 'message', 'role': 'assistant', 'content': 'private-chat-text'})
        self.add('response_item', {'type': 'function_call', 'name': 'request_user_input_async', 'call_id': 'q', 'arguments': json.dumps({'questions': [{'title': '测试题', 'options': ['A', 'B']}]})})
        self.add('response_item', {'type': 'function_call_output', 'call_id': 'q', 'output': '{"accepted":true}'})
        result = module.scan(self.path, 0)
        self.assertEqual(self.path.stat().st_size, result['offset'])
        self.assertEqual(['task_started', 'function_call', 'function_call_output'], [r['type'] for r in result['records']])
        self.assertEqual({'type': 'danger-full-access'}, result['context']['sandbox_policy'])
        self.assertNotIn('private', json.dumps(result))
        self.assertEqual([], module.scan(self.path, result['offset'])['records'])

    def test_partial_record_and_split_utf8_wait_for_newline(self):
        line = (json.dumps({'type': 'event_msg', 'payload': {'type': 'task_started', 'turn_id': '中文'}}, ensure_ascii=False) + '\n').encode()
        split = line.index('中'.encode()) + 1
        self.path.write_bytes(line[:split])
        self.assertEqual(0, module.scan(self.path, 0)['offset'])
        with self.path.open('ab') as stream:
            stream.write(line[split:])
        self.assertEqual('中文', module.scan(self.path, 0)['records'][0]['turn_id'])

    def test_user_message_removes_old_question_and_truncation_resets_cursor(self):
        self.add('response_item', {'type': 'function_call', 'name': 'request_user_input_async', 'arguments': '{}', 'call_id': 'old'})
        self.add('event_msg', {'type': 'user_message', 'message': 'private-new-message'})
        result = module.scan(self.path, 0)
        self.assertEqual([{'type': 'user_message', 'turn_id': ''}], result['records'])
        self.path.write_bytes(b'')
        self.assertTrue(module.scan(self.path, result['offset'])['reset'])

    def test_other_tools_and_rejected_question_results_do_not_appear(self):
        self.add('response_item', {'type': 'function_call', 'name': 'exec', 'arguments': 'private-command', 'call_id': 'cmd'})
        self.add('response_item', {'type': 'function_call_output', 'call_id': 'q', 'output': '{"accepted":false}'})
        self.assertEqual([], module.scan(self.path, 0)['records'])


if __name__ == '__main__':
    unittest.main()
