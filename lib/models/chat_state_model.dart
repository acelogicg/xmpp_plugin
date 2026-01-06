import '../ennums/typing_status.dart';

class ChatState {
  String? from;
  String? senderJid;
  String? id;
  String? type;
  String? msgtype;
  TypingStatus? chatStateType;

  ChatState({
    this.from,
    this.senderJid,
    this.id,
    this.type,
    this.msgtype,
    this.chatStateType,
  });

  Map<String, dynamic> toEventData() {
    return {
      'from': from,
      'senderJid': senderJid,
      'id': id,
      'type': type,
      'msgtype': msgtype,
      'chatStateType': chatStateType?.name ?? '',
    };
  }

  static TypingStatus? _parseTypingStatus(dynamic raw) {
    if (raw == null) return null;
    if (raw is TypingStatus) return raw;
    if (raw is! String) return null;
    var text = raw.trim();
    if (text.isEmpty) return null;
    if (text.contains('.')) {
      text = text.split('.').last;
    }
    final upper = text.toUpperCase();
    for (final v in TypingStatus.values) {
      if (v.name == upper) return v;
    }
    return null;
  }

  factory ChatState.fromJson(dynamic eventData) {
    return ChatState(
      from: eventData['from'] ?? '',
      senderJid: eventData['senderJid'] ?? '',
      id: eventData['id'] ?? '',
      type: eventData['type'] ?? '',
      msgtype: eventData['msgtype'] ?? '',
      chatStateType: _parseTypingStatus(eventData['chatStateType']),
    );
  }
}
