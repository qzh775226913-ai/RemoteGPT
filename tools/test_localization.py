"""Offline checks for UI translations and the raw-content boundary."""
from pathlib import Path
import collections
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
LOCALES = ('values-b+zh+Hans', 'values-b+zh+Hant', 'values-ja', 'values-ko',
           'values-es', 'values-pt-rBR', 'values-de', 'values-fr', 'values-ru')


def resources(folder):
    root = ET.parse(RES / folder / 'strings.xml').getroot()
    result = {}
    for item in root:
        if item.get('translatable') == 'false':
            continue
        key = (item.tag, item.get('name'))
        if key in result:
            raise AssertionError(f'{folder}: duplicate {key}')
        result[key] = item
    return result


def placeholders(text):
    return collections.Counter(re.findall(r'%\d+\$[ds]', text))


class LocalizationResourcesTest(unittest.TestCase):
    def test_all_ten_languages_have_complete_matching_resources(self):
        base = resources('values')
        self.assertGreater(len(base), 300)
        for locale in LOCALES:
            translated = resources(locale)
            self.assertEqual(base.keys(), translated.keys(), locale)
            for key, original in base.items():
                target = translated[key]
                if original.tag == 'plurals':
                    source = original.find("item[@quantity='other']").text
                    self.assertIsNotNone(target.find("item[@quantity='other']"), (locale,key))
                    texts = [item.text for item in target]
                else:
                    source = original.text
                    texts = [target.text]
                for text in texts:
                    self.assertTrue(text and text.strip('" ').strip(), (locale,key))
                    self.assertEqual(placeholders(source), placeholders(text), (locale,key))
                    self.assertNotRegex(text, r'<br|\[\d{4}\]|ZXQ', (locale,key))

    def test_only_explicit_ui_resource_calls_use_the_localization_facade(self):
        java = ROOT / 'app/src/main/java/app/remotessh'
        facade = (java / 'UiText.java').read_text()
        self.assertNotIn('replace(', facade)
        self.assertIn('get(int id', facade)
        # Transport and recovery parsers must not depend on display resources.
        for name in ('RpcClient', 'RpcException', 'SshTransport', 'ConnectionFailure',
                     'ConnectionPreflight', 'ConversationJournal', 'InteractionHistory',
                     'ChatItem', 'ConversationState', 'ConversationStore', 'TurnController'):
            content = (java / (name + '.java')).read_text()
            self.assertNotIn('UiText.', content, name)
            self.assertNotIn('UiPresentation.', content, name)

    def test_display_setters_do_not_gain_hardcoded_labels(self):
        java = ROOT / 'app/src/main/java/app/remotessh'
        setters = ('setText|setTitle|setMessage|setHint|setContentDescription|'
                   'setTooltipText|setPositiveButton|setNegativeButton|setNeutralButton')
        for path in java.glob('*.java'):
            for value in re.findall(r'\b(?:' + setters + r')\("([^"\\]*)"', path.read_text()):
                self.assertNotRegex(value, '[A-Za-z\u3400-\u9fff]', path.name)

    def test_known_ui_entry_points_do_not_reintroduce_literal_chinese(self):
        java = ROOT / 'app/src/main/java/app/remotessh'
        permitted = {
            'MainActivity': {'正在停止', '无法写入所选位置', '连接已切换，请重新下载'},
            'ProfileEditor': {'无法读取文件', '私钥文件不能超过 64 KiB'},
            'LiveStatusNotifications': {'正在停止'},
            'PermissionPicker': {'新的审批权限生效前，客户端将会自动允许全部审批请求，继续吗？'},
            'QuestionHistoryView': {'回答：'},
        }
        names = ('MainActivity ProfileEditor ModelPicker DialogUi RemoteBrowser DirectoryPicker '
                 'QuestionForm QuestionHistoryView ImageDialog ImagePicker FilePicker PermissionPicker '
                 'ConnectionSetupUi MessageBodyView MessageImageTile LiveProgressTextView '
                 'BubbleActivity ConnectionService LiveStatusNotifications MathContentView TableContentView ZoomImageView').split()
        for name in names:
            for literal in re.findall(r'"(?:\\.|[^"\\])*"', (java / (name+'.java')).read_text()):
                if re.search('[\u3400-\u9fff]', literal):
                    self.assertIn(literal[1:-1], permitted.get(name,set()), (name,literal))


if __name__ == '__main__':
    unittest.main()
