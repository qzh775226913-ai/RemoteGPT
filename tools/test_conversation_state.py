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

    def test_archive_keeps_question_after_new_task_and_only_app_formatted_answers(self):
        self.add('event_msg', {'type': 'task_started', 'turn_id': 'first'})
        self.add('response_item', {'type': 'function_call', 'name': 'request_user_input_async', 'arguments': '{"questions":[{"title":"Q"}]}', 'call_id': 'q'})
        self.add('response_item', {'type': 'function_call_output', 'call_id': 'q', 'output': '{"accepted":true}'})
        self.add('event_msg', {'type': 'task_started', 'turn_id': 'second'})
        self.add('event_msg', {'type': 'user_message', 'message': '回答上一轮的问题：\nQ\nA\n\n'})
        self.add('event_msg', {'type': 'user_message', 'message': 'private-normal-chat'})
        result = module.scan(self.path, 0)
        self.assertEqual(['task_started', 'function_call', 'function_call_output', 'task_started', 'user_answer'], [x['type'] for x in result['history']])
        self.assertNotIn('private-normal-chat', json.dumps(result))
        self.assertNotIn('function_call', [x['type'] for x in result['records']])

    def test_native_answers_and_metadata_stay_bounded_across_chunks(self):
        for i in range(30):
            self.add('response_item', {'type': 'function_call', 'name': 'request_user_input', 'call_id': str(i), 'arguments': json.dumps({'questions': [{'question': 'Q' * 1800, 'id': 'q'}]})})
            self.add('response_item', {'type': 'function_call_output', 'call_id': str(i), 'output': '{"answers":{"q":{"answers":["A"]}}}'})
        offset, calls, answers = 0, [], []
        while offset < self.path.stat().st_size:
            result = module.scan(self.path, offset, calls)
            self.assertGreater(result['offset'], offset)
            self.assertLess(len(json.dumps(result, ensure_ascii=False).encode()), 65536)
            calls.extend(x['call_id'] for x in result['history'] if x['type'] == 'function_call')
            answers.extend(x['call_id'] for x in result['history'] if x['type'] == 'function_call_output')
            offset = result['offset']
        self.assertEqual(30, len(calls))
        self.assertEqual(30, len(answers))

    def test_user_response_items_recover_answers_without_returning_other_messages(self):
        self.add('response_item', {'type': 'function_call', 'name': 'request_user_input_async', 'arguments': '{}', 'call_id': 'old'})
        self.add('response_item', {'type': 'message', 'role': 'user', 'content': [{'type': 'input_text', 'text': '回答上一轮的问题：\nQ\nA\n\n'}], 'private_metadata': 'private-context'})
        self.add('response_item', {'type': 'message', 'role': 'user', 'content': [{'type': 'input_text', 'text': 'private-normal-chat'}]})
        result = module.scan(self.path, 0)
        self.assertEqual([{'type': 'user_message', 'turn_id': ''}], result['records'])
        self.assertEqual(1, sum(x['type'] == 'user_answer' for x in result['history']))
        self.assertNotIn('private', json.dumps(result))

    def test_unrelated_tool_answer_outputs_are_not_archived(self):
        self.add('response_item', {'type': 'function_call_output', 'call_id': 'other-tool', 'output': '{"answers":{"private":"private-output"}}'})
        result = module.scan(self.path, 0)
        self.assertEqual([], result['history'])
        self.assertNotIn('private', json.dumps(result))


if __name__ == '__main__':
    unittest.main()
